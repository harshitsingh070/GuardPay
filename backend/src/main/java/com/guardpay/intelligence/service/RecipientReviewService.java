package com.guardpay.intelligence.service;

import com.guardpay.intelligence.domain.EvidenceSource;
import com.guardpay.intelligence.domain.EvidenceType;
import com.guardpay.intelligence.domain.EvidenceVerificationStatus;
import com.guardpay.intelligence.domain.FraudIntelligenceReport;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.RecipientFraudEvidence;
import com.guardpay.intelligence.domain.RecipientReviewEvent;
import com.guardpay.intelligence.domain.ReportStatus;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.domain.ReviewAction;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.repository.FraudIntelligenceReportRepository;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.repository.RecipientFraudEvidenceRepository;
import com.guardpay.intelligence.repository.RecipientReviewEventRepository;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin review workflow for global recipient reputation.
 *
 * <p>Only this service can set {@code CONFIRMED_FRAUD}. Every action writes an
 * append-only review event; rejection revokes the linked report evidence
 * instead of deleting history.
 */
@Service
public class RecipientReviewService {

  private static final List<ReportStatus> OPEN_STATUSES =
      List.of(ReportStatus.SUBMITTED, ReportStatus.UNDER_REVIEW);

  private final GlobalRecipientEntityRepository entities;
  private final FraudIntelligenceReportRepository reports;
  private final RecipientFraudEvidenceRepository evidence;
  private final RecipientReviewEventRepository reviewEvents;
  private final RecipientReputationService reputation;

  public RecipientReviewService(
      GlobalRecipientEntityRepository entities,
      FraudIntelligenceReportRepository reports,
      RecipientFraudEvidenceRepository evidence,
      RecipientReviewEventRepository reviewEvents,
      RecipientReputationService reputation) {
    this.entities = entities;
    this.reports = reports;
    this.evidence = evidence;
    this.reviewEvents = reviewEvents;
    this.reputation = reputation;
  }

  @Transactional
  public void review(UUID entityId, User admin, ReviewAction action, String note) {
    if (admin == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (action == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review action is required");
    }
    GlobalRecipientEntity entity =
        entities
            .findByIdForUpdate(entityId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient"));
    ReputationStatus from = entity.getReputationStatus();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String cleanNote = sanitize(note);

    switch (action) {
      case CONFIRM_FRAUD -> {
        if (from == ReputationStatus.CONFIRMED_FRAUD) {
          break;
        }
        requireTransition(from, ReputationStatus.CONFIRMED_FRAUD);
        reputation.applyStatus(entity, ReputationStatus.CONFIRMED_FRAUD);
        reputation.recordEvidence(
            entity,
            EvidenceType.FRAUD_CONFIRMED,
            EvidenceSource.CONFIRMED_FRAUD_INCIDENT,
            1.0,
            EvidenceVerificationStatus.VERIFIED,
            fingerprint(entity.getId(), "CONFIRMED_FRAUD", admin.getId()),
            null,
            null,
            cleanNote,
            now,
            null);
        closeOpenReports(entity, ReportStatus.ACCEPTED, admin, cleanNote, now);
      }
      case CORROBORATE -> {
        if (from == ReputationStatus.CONFIRMED_FRAUD) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "A confirmed fraud case cannot be downgraded");
        }
        requireTransition(from, ReputationStatus.CORROBORATED_SUSPICIOUS);
        reputation.applyStatus(entity, ReputationStatus.CORROBORATED_SUSPICIOUS);
        reputation.recordEvidence(
            entity,
            EvidenceType.MANUAL_CORROBORATION,
            EvidenceSource.MANUAL_REVIEW,
            0.9,
            EvidenceVerificationStatus.VERIFIED,
            fingerprint(entity.getId(), "MANUAL_REVIEW", admin.getId()),
            null,
            null,
            cleanNote,
            now,
            null);
        closeOpenReports(entity, ReportStatus.UNDER_REVIEW, admin, cleanNote, now);
      }
      case REJECT -> {
        if (from == ReputationStatus.CONFIRMED_FRAUD) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "A confirmed fraud case cannot be rejected");
        }
        for (FraudIntelligenceReport report :
            reports.findByRecipientEntityIdAndReportStatusIn(entity.getId(), OPEN_STATUSES)) {
          report.setReportStatus(ReportStatus.REJECTED);
          report.setReviewNote(cleanNote);
          report.setReviewedAt(now);
          report.setReviewerReference(admin.getId());
          reports.save(report);
          reputation.revokeEvidenceForReport(report, "Rejected by admin review");
        }
        ReputationStatus next = statusAfterRejection(entity.getId());
        if (next != from && from.canTransitionTo(next)) {
          reputation.applyStatus(entity, next);
        }
      }
      case CLEAR -> {
        requireTransition(from, ReputationStatus.CLEARED);
        reputation.applyStatus(entity, ReputationStatus.CLEARED);
      }
      case REOPEN -> {
        requireTransition(from, ReputationStatus.UNDER_REVIEW);
        reputation.applyStatus(entity, ReputationStatus.UNDER_REVIEW);
      }
    }

    reputation.recalculate(entity);

    RecipientReviewEvent event = new RecipientReviewEvent();
    event.setRecipientEntity(entity);
    event.setActorReference(admin.getId());
    event.setAction(action);
    event.setFromStatus(from);
    event.setToStatus(entity.getReputationStatus());
    event.setNote(cleanNote);
    reviewEvents.save(event);
  }

  private ReputationStatus statusAfterRejection(UUID entityId) {
    List<RecipientFraudEvidence> remaining =
        evidence.findByRecipientEntityIdAndRevokedAtIsNull(entityId).stream()
            .filter(row -> row.getVerificationStatus() != EvidenceVerificationStatus.REJECTED)
            .toList();
    if (remaining.isEmpty()) {
      return ReputationStatus.UNKNOWN;
    }
    boolean strong =
        remaining.stream()
            .anyMatch(
                row -> row.getSource() != null && row.getSource().reliability() >= 0.5);
    return strong ? ReputationStatus.UNDER_REVIEW : ReputationStatus.REPORTED;
  }

  private void closeOpenReports(
      GlobalRecipientEntity entity,
      ReportStatus target,
      User admin,
      String note,
      OffsetDateTime now) {
    for (FraudIntelligenceReport report :
        reports.findByRecipientEntityIdAndReportStatusIn(entity.getId(), OPEN_STATUSES)) {
      report.setReportStatus(target);
      report.setReviewNote(note);
      report.setReviewedAt(now);
      report.setReviewerReference(admin.getId());
      reports.save(report);
    }
  }

  private static void requireTransition(ReputationStatus from, ReputationStatus target) {
    if (from == target) {
      return;
    }
    if (!from.canTransitionTo(target)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Invalid reputation transition " + from + " -> " + target);
    }
  }

  private static String fingerprint(UUID entityId, String action, UUID actor) {
    return RecipientIdentifierHasher.sha256Hex(entityId + "|" + action + "|" + actor);
  }

  private static String sanitize(String note) {
    if (note == null || note.isBlank()) {
      return null;
    }
    String cleaned = note.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    return cleaned.length() > 1000 ? cleaned.substring(0, 1000) : cleaned;
  }
}
