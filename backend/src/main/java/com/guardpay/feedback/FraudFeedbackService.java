package com.guardpay.feedback;

import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.service.RecipientIdentityService;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Captures explicit fraud/legitimate labels and turns them into evaluation
 * metrics. The labels are the feedback loop: only admin-verified labels feed
 * the learning dataset builder, and no feedback ever changes a live decision.
 */
@Service
public class FraudFeedbackService {

  private final FraudFeedbackRepository feedback;
  private final TransactionRepository transactions;
  private final RiskAssessmentRepository assessments;
  private final FraudFeedbackEventRepository events;
  private final FraudFeedbackReviewRepository reviews;
  private final FraudFeedbackEvidenceRepository evidence;
  private final RecipientIdentityService identity;
  private final List<FeedbackChangeListener> changeListeners;

  /** Legacy/test constructor: history and learning invalidation are not recorded. */
  public FraudFeedbackService(
      FraudFeedbackRepository feedback,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments) {
    this(feedback, transactions, assessments, null, null, null, null, List.of());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public FraudFeedbackService(
      FraudFeedbackRepository feedback,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      FraudFeedbackEventRepository events,
      FraudFeedbackReviewRepository reviews,
      FraudFeedbackEvidenceRepository evidence,
      RecipientIdentityService identity,
      List<FeedbackChangeListener> changeListeners) {
    this.feedback = feedback;
    this.transactions = transactions;
    this.assessments = assessments;
    this.events = events;
    this.reviews = reviews;
    this.evidence = evidence;
    this.identity = identity;
    this.changeListeners = changeListeners == null ? List.of() : changeListeners;
  }

  @Transactional
  public FraudFeedbackDto submit(User user, UUID transactionId, FeedbackLabel label) {
    return submit(user, transactionId, new SubmitFeedbackRequest(label), null);
  }

  @Transactional
  public FraudFeedbackDto submit(
      User user, UUID transactionId, SubmitFeedbackRequest request, String idempotencyHeader) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (transactionId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is required");
    }
    if (request == null || request.label() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Label is required");
    }
    Transaction transaction =
        transactions
            .findByIdAndUserId(transactionId, user.getId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction"));

    String idempotencyKey = firstNonBlank(request.idempotencyKey(), idempotencyHeader);
    Optional<FraudFeedback> existingRow =
        feedback.findByTransactionIdAndUserReference(transactionId, user.getId());
    if (existingRow.isPresent()
        && idempotencyKey != null
        && idempotencyKey.equals(existingRow.get().getIdempotencyKey())) {
      return toDto(existingRow.get());
    }
    if (existingRow.isPresent()
        && !user.getId().equals(existingRow.get().getUserReference())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Feedback belongs to another user");
    }

    FraudFeedback row = existingRow.orElseGet(FraudFeedback::new);
    FeedbackStatus previousStatus = row.getFeedbackStatus();
    FeedbackLabel previousLabel = row.getLabel();
    FeedbackType previousType = row.getFeedbackType();
    FeedbackReasonCode previousReason = row.getReasonCode();
    boolean changed =
        row.getId() != null
            && (previousLabel != request.label()
                || previousType != feedbackType(request)
                || previousReason != reasonCode(request));

    RiskAssessment latest =
        assessments.findByTransactionOrderByCreatedAtDesc(transaction).stream()
            .findFirst()
            .orElse(null);

    row.setTransactionId(transactionId);
    row.setUserReference(user.getId());
    row.setLabel(request.label());
    row.setFeedbackType(feedbackType(request));
    row.setReasonCode(reasonCode(request));
    row.setSource(user.isAdmin() ? FeedbackSource.ADMIN : FeedbackSource.USER);
    row.setDecisionAction(
        latest != null && latest.getRecommendedAction() != null
            ? latest.getRecommendedAction().name()
            : null);
    row.setRiskScore(latest != null ? latest.getScore() : null);
    row.setRiskAssessmentId(latest != null ? latest.getId() : null);
    row.setRelatedFindingCode(sanitizeCode(request.relatedFindingCode()));
    row.setRecipientEntityId(resolveRecipientEntityId(transaction));
    row.setIdempotencyKey(idempotencyKey);
    row.setReviewerReference(null);
    row.setReviewerConfidence(null);
    row.setReviewedAt(null);
    row.setFeedbackStatus(FeedbackStatus.SUBMITTED);
    row.setEvidenceFingerprint(
        RecipientIdentifierHasher.sha256Hex(
            transactionId
                + "|"
                + user.getId()
                + "|"
                + request.label()
                + "|"
                + row.getFeedbackType()
                + "|"
                + row.getReasonCode()));
    FraudFeedback saved = feedback.save(row);

    if (events != null) {
      recordEvent(
          saved,
          user.getId(),
          changed ? FeedbackEventType.UPDATED : FeedbackEventType.SUBMITTED,
          previousStatus,
          saved.getFeedbackStatus(),
          changed ? "Label corrected by user" : "Label submitted");
    }
    if (evidence != null) {
      recordDecisionEvidence(saved);
    }
    if (changed && previousStatus != null && previousStatus.isVerified()) {
      notifyChange(saved, "Verified label corrected by user");
    } else if (changed) {
      notifyChange(saved, "Feedback label corrected");
    }
    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public Optional<FraudFeedbackDto> forTransaction(User user, UUID transactionId) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    transactions
        .findByIdAndUserId(transactionId, user.getId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction"));
    return feedback
        .findByTransactionIdAndUserReference(transactionId, user.getId())
        .map(FraudFeedbackService::toDto);
  }

  @Transactional(readOnly = true)
  public Page<FraudFeedbackDto> mine(User user, int page, int size) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return feedback
        .findByUserReferenceOrderByCreatedAtDesc(user.getId(), pageable(page, size))
        .map(FraudFeedbackService::toDto);
  }

  @Transactional(readOnly = true)
  public FeedbackSummaryDto summary(User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    List<FraudFeedback> rows = feedback.findAllByOrderByCreatedAtDesc().stream()
        .filter(row -> user.getId().equals(row.getUserReference()))
        .toList();
    return summaryOf(rows);
  }

  @Transactional(readOnly = true)
  public Page<FraudFeedbackDto> adminList(FeedbackStatus status, int page, int size) {
    Pageable pageable = pageable(page, size);
    return status == null
        ? feedback.findAll(pageable).map(FraudFeedbackService::toDto)
        : feedback.findByFeedbackStatusOrderByCreatedAtDesc(status, pageable)
            .map(FraudFeedbackService::toDto);
  }

  @Transactional(readOnly = true)
  public FeedbackDetailDto adminDetail(UUID id) {
    FraudFeedback row =
        feedback
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feedback"));
    return detail(row);
  }

  @Transactional(readOnly = true)
  public FeedbackDetailDto detail(User user, UUID id) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    FraudFeedback row =
        feedback
            .findById(id)
            .filter(candidate -> user.getId().equals(candidate.getUserReference()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feedback"));
    return detail(row);
  }

  private FeedbackDetailDto detail(FraudFeedback row) {
    List<FeedbackEventDto> eventDtos =
        events == null
            ? List.of()
            : events.findByFeedbackIdOrderByOccurredAtDesc(row.getId()).stream()
                .map(
                    event ->
                        new FeedbackEventDto(
                            event.getEventType().name(),
                            name(event.getFromStatus()),
                            name(event.getToStatus()),
                            event.getDetail(),
                            event.getOccurredAt()))
                .toList();
    List<FeedbackReviewDto> reviewDtos =
        reviews == null
            ? List.of()
            : reviews.findByFeedbackIdOrderByReviewedAtDesc(row.getId()).stream()
                .map(
                    review ->
                        new FeedbackReviewDto(
                            review.getAction().name(),
                            name(review.getFromStatus()),
                            name(review.getToStatus()),
                            review.getConfidence() == null
                                ? null
                                : review.getConfidence().doubleValue(),
                            review.getNote(),
                            review.getReviewedAt()))
                .toList();
    return new FeedbackDetailDto(toDto(row), eventDtos, reviewDtos);
  }

  @Transactional(readOnly = true)
  public FeedbackMetricsDto metrics() {
    return metrics(feedback.findAllByOrderByCreatedAtDesc());
  }

  @Transactional(readOnly = true)
  public FeedbackSummaryDto adminSummary() {
    return summaryOf(feedback.findAllByOrderByCreatedAtDesc());
  }

  @Transactional(readOnly = true)
  public List<FraudFeedbackDto> export() {
    return feedback.findAllByOrderByCreatedAtDesc().stream()
        .map(FraudFeedbackService::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FraudFeedback> verifiedFeedback() {
    return feedback.findByFeedbackStatusInOrderByCreatedAtAsc(
        List.of(FeedbackStatus.VERIFIED_FRAUD, FeedbackStatus.VERIFIED_LEGITIMATE));
  }

  void recordEvent(
      FraudFeedback row,
      UUID actor,
      FeedbackEventType type,
      FeedbackStatus from,
      FeedbackStatus to,
      String detail) {
    if (row == null || type == null || events == null || row.getId() == null) {
      return;
    }
    FraudFeedbackEvent event = new FraudFeedbackEvent();
    event.setFeedback(row);
    event.setActorReference(actor);
    event.setEventType(type);
    event.setFromStatus(from);
    event.setToStatus(to);
    event.setDetail(sanitizeDetail(detail));
    event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
    events.save(event);
  }

  static String sanitizeDetail(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String cleaned = value.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    return cleaned.length() > 1000 ? cleaned.substring(0, 1000) : cleaned;
  }

  private void recordDecisionEvidence(FraudFeedback row) {
    if (row.getId() == null || row.getEvidenceFingerprint() == null) {
      return;
    }
    if (evidence.findByFeedbackIdAndEvidenceFingerprint(row.getId(), row.getEvidenceFingerprint())
        .isPresent()) {
      return;
    }
    FraudFeedbackEvidence snapshot = new FraudFeedbackEvidence();
    snapshot.setFeedback(row);
    snapshot.setEvidenceType(FeedbackEvidenceType.DECISION_SNAPSHOT);
    snapshot.setEvidenceFingerprint(row.getEvidenceFingerprint());
    snapshot.setRelatedFindingCode(row.getRelatedFindingCode());
    snapshot.setRelatedTransactionId(row.getTransactionId());
    snapshot.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC));
    snapshot.setVerified(false);
    evidence.save(snapshot);
  }

  private UUID resolveRecipientEntityId(Transaction transaction) {
    if (identity == null || transaction.getRecipient() == null) {
      return null;
    }
    try {
      return identity
          .findForRecipient(transaction.getRecipient())
          .map(match -> match.entity().getId())
          .orElse(null);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void notifyChange(FraudFeedback row, String reason) {
    for (FeedbackChangeListener listener : changeListeners) {
      try {
        listener.onFeedbackChanged(row, reason);
      } catch (RuntimeException ignored) {
        // Learning invalidation is best effort; the feedback write still succeeds.
      }
    }
  }

  private static FeedbackType feedbackType(SubmitFeedbackRequest request) {
    if (request.feedbackType() != null) {
      return request.feedbackType();
    }
    return request.label() == FeedbackLabel.FRAUD
        ? FeedbackType.SCAM_OR_SOCIAL_ENGINEERING
        : FeedbackType.LEGITIMATE_PAYMENT;
  }

  private static FeedbackReasonCode reasonCode(SubmitFeedbackRequest request) {
    if (request.reasonCode() != null) {
      return request.reasonCode();
    }
    return request.label() == FeedbackLabel.FRAUD
        ? FeedbackReasonCode.OTHER
        : FeedbackReasonCode.NONE;
  }

  private static String sanitizeCode(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String cleaned = value.trim().toUpperCase(java.util.Locale.ROOT);
    if (!cleaned.matches("[A-Z0-9_]{1,64}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid finding code");
    }
    return cleaned;
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first.trim();
    }
    if (second != null && !second.isBlank()) {
      return second.trim();
    }
    return null;
  }

  private static Pageable pageable(int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(size, 100));
    return PageRequest.of(safePage, safeSize);
  }

  private static FeedbackSummaryDto summaryOf(List<FraudFeedback> rows) {
    long submitted = 0;
    long underReview = 0;
    long verifiedFraud = 0;
    long verifiedLegitimate = 0;
    long rejected = 0;
    long superseded = 0;
    for (FraudFeedback row : rows) {
      switch (row.getFeedbackStatus() == null
          ? FeedbackStatus.SUBMITTED
          : row.getFeedbackStatus()) {
        case SUBMITTED -> submitted++;
        case UNDER_REVIEW -> underReview++;
        case VERIFIED_FRAUD -> verifiedFraud++;
        case VERIFIED_LEGITIMATE -> verifiedLegitimate++;
        case REJECTED -> rejected++;
        case SUPERSEDED -> superseded++;
      }
    }
    return new FeedbackSummaryDto(
        rows.size(),
        submitted,
        underReview,
        verifiedFraud,
        verifiedLegitimate,
        rejected,
        superseded);
  }

  private static FeedbackMetricsDto metrics(List<FraudFeedback> rows) {
    long truePositive = 0;
    long falsePositive = 0;
    long trueNegative = 0;
    long falseNegative = 0;
    long unclassified = 0;
    long fraudLabels = 0;
    long legitimateLabels = 0;

    for (FraudFeedback row : rows) {
      if (row.getLabel() == FeedbackLabel.FRAUD) {
        fraudLabels++;
      } else if (row.getLabel() == FeedbackLabel.LEGITIMATE) {
        legitimateLabels++;
      }
      String action = row.getDecisionAction();
      if (action == null) {
        unclassified++;
        continue;
      }
      boolean flagged = "VERIFY".equals(action) || "STOP".equals(action);
      if (row.getLabel() == FeedbackLabel.FRAUD) {
        if (flagged) {
          truePositive++;
        } else {
          falseNegative++;
        }
      } else if (row.getLabel() == FeedbackLabel.LEGITIMATE) {
        if (flagged) {
          falsePositive++;
        } else {
          trueNegative++;
        }
      }
    }

    Double precision =
        truePositive + falsePositive == 0
            ? null
            : (double) truePositive / (truePositive + falsePositive);
    Double recall =
        truePositive + falseNegative == 0
            ? null
            : (double) truePositive / (truePositive + falseNegative);
    return new FeedbackMetricsDto(
        rows.size(),
        fraudLabels,
        legitimateLabels,
        truePositive,
        falsePositive,
        trueNegative,
        falseNegative,
        unclassified,
        precision,
        recall);
  }

  static FraudFeedbackDto toDto(FraudFeedback row) {
    return new FraudFeedbackDto(
        row.getId(),
        row.getTransactionId(),
        name(row.getLabel()),
        name(row.getFeedbackType()),
        name(row.getFeedbackStatus()),
        name(row.getSource()),
        row.getDecisionAction(),
        row.getRiskScore(),
        row.getRiskAssessmentId(),
        row.getRecipientEntityId(),
        row.getRelatedFindingCode(),
        name(row.getReasonCode()),
        row.getReviewerReference() == null ? null : row.getReviewerReference().toString(),
        row.getReviewerConfidence() == null ? null : row.getReviewerConfidence().doubleValue(),
        row.getReviewedAt(),
        row.getCreatedAt(),
        row.getUpdatedAt());
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
