package com.guardpay.feedback;

import com.guardpay.intelligence.domain.EvidenceSource;
import com.guardpay.intelligence.domain.EvidenceType;
import com.guardpay.intelligence.domain.EvidenceVerificationStatus;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.service.RecipientReputationService;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only review of explicit feedback labels.
 *
 * <p>A user cannot verify their own label. Verification is the only path that
 * makes a label eligible for learning, and a verified fraud label is also
 * bridged to recipient intelligence as verified investigation evidence. It can
 * raise a recipient to {@code UNDER_REVIEW}, never to {@code CONFIRMED_FRAUD};
 * that transition remains exclusively in the recipient review service.
 */
@Service
public class FraudFeedbackReviewService {

  private final FraudFeedbackRepository feedback;
  private final FraudFeedbackEventRepository events;
  private final FraudFeedbackReviewRepository reviews;
  private final GlobalRecipientEntityRepository entities;
  private final RecipientReputationService reputation;
  private final List<FeedbackChangeListener> changeListeners;

  /** Legacy/test constructor: intelligence bridge is disabled. */
  public FraudFeedbackReviewService(
      FraudFeedbackRepository feedback,
      FraudFeedbackEventRepository events,
      FraudFeedbackReviewRepository reviews) {
    this(feedback, events, reviews, null, null, List.of());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public FraudFeedbackReviewService(
      FraudFeedbackRepository feedback,
      FraudFeedbackEventRepository events,
      FraudFeedbackReviewRepository reviews,
      GlobalRecipientEntityRepository entities,
      RecipientReputationService reputation,
      List<FeedbackChangeListener> changeListeners) {
    this.feedback = feedback;
    this.events = events;
    this.reviews = reviews;
    this.entities = entities;
    this.reputation = reputation;
    this.changeListeners = changeListeners == null ? List.of() : changeListeners;
  }

  @Transactional
  public FraudFeedbackDto review(
      UUID feedbackId, User admin, FeedbackReviewRequest request) {
    if (admin == null || !admin.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
    if (request == null || request.action() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review action is required");
    }
    FraudFeedback row =
        feedback
            .findByIdForUpdate(feedbackId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feedback"));
    FeedbackStatus from = row.getFeedbackStatus() == null
        ? FeedbackStatus.SUBMITTED
        : row.getFeedbackStatus();
    FeedbackStatus to = targetStatus(from, request.action());
    BigDecimal confidence = confidence(request.confidence(), request.action());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String note = FraudFeedbackService.sanitizeDetail(request.note());

    if (request.action() == FeedbackReviewAction.VERIFY_FRAUD) {
      row.setLabel(FeedbackLabel.FRAUD);
      row.setFeedbackType(FeedbackType.SCAM_OR_SOCIAL_ENGINEERING);
      if (row.getReasonCode() == null || row.getReasonCode() == FeedbackReasonCode.NONE) {
        row.setReasonCode(FeedbackReasonCode.OTHER);
      }
    } else if (request.action() == FeedbackReviewAction.VERIFY_LEGITIMATE) {
      row.setLabel(FeedbackLabel.LEGITIMATE);
      row.setFeedbackType(FeedbackType.LEGITIMATE_PAYMENT);
      row.setReasonCode(FeedbackReasonCode.NONE);
    }

    row.setFeedbackStatus(to);
    row.setReviewerReference(admin.getId());
    row.setReviewerConfidence(confidence);
    row.setReviewedAt(to.isVerified() || to == FeedbackStatus.REJECTED ? now : null);
    FraudFeedback saved = feedback.save(row);

    FraudFeedbackEvent event = new FraudFeedbackEvent();
    event.setFeedback(saved);
    event.setActorReference(admin.getId());
    event.setEventType(eventType(request.action()));
    event.setFromStatus(from);
    event.setToStatus(to);
    event.setDetail(note);
    event.setOccurredAt(now);
    events.save(event);

    FraudFeedbackReview review = new FraudFeedbackReview();
    review.setFeedback(saved);
    review.setReviewerReference(admin.getId());
    review.setAction(request.action());
    review.setFromStatus(from);
    review.setToStatus(to);
    review.setConfidence(confidence);
    review.setNote(note);
    review.setReviewedAt(now);
    reviews.save(review);

    if (request.action() == FeedbackReviewAction.VERIFY_FRAUD) {
      bridgeVerifiedFraud(saved, admin, confidence, now);
    }
    if (from.isVerified() || to.isVerified()) {
      notifyChange(saved, "Admin review changed verification state");
    }
    return FraudFeedbackService.toDto(saved);
  }

  private void bridgeVerifiedFraud(
      FraudFeedback row, User admin, BigDecimal confidence, OffsetDateTime now) {
    if (entities == null || reputation == null || row.getRecipientEntityId() == null) {
      return;
    }
    GlobalRecipientEntity entity = entities.findById(row.getRecipientEntityId()).orElse(null);
    if (entity == null) {
      return;
    }
    String fingerprint =
        RecipientIdentifierHasher.sha256Hex(
            entity.getId() + "|VERIFIED_FEEDBACK|" + row.getId());
    reputation.recordEvidence(
        entity,
        EvidenceType.INVESTIGATION_CONFIRMED,
        EvidenceSource.VERIFIED_INVESTIGATION,
        confidence == null ? 0.9 : confidence.doubleValue(),
        EvidenceVerificationStatus.VERIFIED,
        fingerprint,
        RecipientIdentifierHasher.sha256Hex(entity.getId() + "|" + admin.getId())
            .substring(0, 32),
        row.getTransactionId(),
        now,
        null);
    reputation.recalculate(entity);
  }

  private static FeedbackStatus targetStatus(
      FeedbackStatus from, FeedbackReviewAction action) {
    return switch (action) {
      case START_REVIEW -> {
        if (from == FeedbackStatus.SUPERSEDED) {
          throw conflict(from, FeedbackStatus.UNDER_REVIEW);
        }
        yield FeedbackStatus.UNDER_REVIEW;
      }
      case VERIFY_FRAUD -> FeedbackStatus.VERIFIED_FRAUD;
      case VERIFY_LEGITIMATE -> FeedbackStatus.VERIFIED_LEGITIMATE;
      case REJECT -> {
        if (from.isVerified()) {
          throw conflict(from, FeedbackStatus.REJECTED);
        }
        yield FeedbackStatus.REJECTED;
      }
      case REOPEN -> {
        if (from == FeedbackStatus.SUBMITTED) {
          throw conflict(from, FeedbackStatus.UNDER_REVIEW);
        }
        yield FeedbackStatus.UNDER_REVIEW;
      }
    };
  }

  private static ResponseStatusException conflict(
      FeedbackStatus from, FeedbackStatus to) {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "Invalid feedback transition " + from + " -> " + to);
  }

  private static BigDecimal confidence(BigDecimal supplied, FeedbackReviewAction action) {
    if (!action.name().startsWith("VERIFY")) {
      return null;
    }
    BigDecimal value = supplied == null ? BigDecimal.valueOf(0.9) : supplied;
    if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Review confidence must be between 0 and 1");
    }
    return value.setScale(3, RoundingMode.HALF_UP);
  }

  private static FeedbackEventType eventType(FeedbackReviewAction action) {
    return switch (action) {
      case START_REVIEW -> FeedbackEventType.REVIEW_STARTED;
      case VERIFY_FRAUD, VERIFY_LEGITIMATE -> FeedbackEventType.VERIFIED;
      case REJECT -> FeedbackEventType.REJECTED;
      case REOPEN -> FeedbackEventType.REOPENED;
    };
  }

  private void notifyChange(FraudFeedback row, String reason) {
    for (FeedbackChangeListener listener : changeListeners) {
      try {
        listener.onFeedbackChanged(row, reason);
      } catch (RuntimeException ignored) {
        // Dataset invalidation is best effort; the review still succeeds.
      }
    }
  }
}
