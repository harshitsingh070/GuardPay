package com.guardpay.intelligence.service;

import com.guardpay.intelligence.config.RecipientIntelligenceProperties;
import com.guardpay.intelligence.domain.EvidenceSource;
import com.guardpay.intelligence.domain.EvidenceType;
import com.guardpay.intelligence.domain.EvidenceVerificationStatus;
import com.guardpay.intelligence.domain.FraudIntelligenceReport;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.RecipientFraudEvidence;
import com.guardpay.intelligence.domain.RecipientReputationSnapshot;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.repository.RecipientFraudEvidenceRepository;
import com.guardpay.intelligence.repository.RecipientReputationSnapshotRepository;
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
 * Maintains deduplicated evidence and derives the stored reputation fields.
 *
 * <p>Status transitions caused by evidence are conservative: user reports can
 * move an entity to {@code REPORTED}, provider outcomes to {@code UNDER_REVIEW},
 * but only an admin review can set {@code CONFIRMED_FRAUD}.
 */
@Service
public class RecipientReputationService {

  private final GlobalRecipientEntityRepository entities;
  private final RecipientFraudEvidenceRepository evidence;
  private final RecipientReputationSnapshotRepository snapshots;
  private final RecipientReputationCalculator calculator;
  private final RecipientIntelligenceProperties properties;

  public RecipientReputationService(
      GlobalRecipientEntityRepository entities,
      RecipientFraudEvidenceRepository evidence,
      RecipientReputationSnapshotRepository snapshots,
      RecipientReputationCalculator calculator,
      RecipientIntelligenceProperties properties) {
    this.entities = entities;
    this.evidence = evidence;
    this.snapshots = snapshots;
    this.calculator = calculator;
    this.properties = properties;
  }

  @Transactional
  public RecipientFraudEvidence recordEvidence(
      GlobalRecipientEntity entity,
      EvidenceType type,
      EvidenceSource source,
      double confidence,
      EvidenceVerificationStatus verification,
      String fingerprint,
      String reporterGroupKey,
      UUID relatedTransactionId,
      String details,
      OffsetDateTime observedAt,
      FraudIntelligenceReport sourceReport) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime effectiveObserved = observedAt == null ? now : observedAt;
    RecipientFraudEvidence row =
        evidence
            .findByRecipientEntityIdAndEvidenceFingerprint(entity.getId(), fingerprint)
            .orElse(null);
    if (row == null) {
      row = new RecipientFraudEvidence();
      row.setRecipientEntity(entity);
      row.setEvidenceType(type);
      row.setSource(source);
      row.setVerificationStatus(verification);
      row.setConfidence(clamp(confidence));
      row.setEvidenceFingerprint(fingerprint);
      row.setReporterGroupKey(reporterGroupKey);
      row.setRelatedTransactionId(relatedTransactionId);
      row.setDetails(details);
      row.setObservedAt(effectiveObserved);
      row.setLastObservedAt(effectiveObserved);
      row.setReportCount(1);
      row.setSourceReport(sourceReport);
    } else {
      row.setLastObservedAt(now);
      row.setReportCount(row.getReportCount() + 1);
      if (clamp(confidence).compareTo(row.getConfidence()) > 0) {
        row.setConfidence(clamp(confidence));
      }
      if (verification == EvidenceVerificationStatus.VERIFIED
          && row.getVerificationStatus() != EvidenceVerificationStatus.VERIFIED) {
        row.setVerificationStatus(EvidenceVerificationStatus.VERIFIED);
      }
      if (row.getSourceReport() == null && sourceReport != null) {
        row.setSourceReport(sourceReport);
      }
      if ((row.getDetails() == null || row.getDetails().isBlank())
          && details != null
          && !details.isBlank()) {
        row.setDetails(details);
      }
    }
    evidence.save(row);
    advanceStatus(entity, source, verification);
    entities.save(entity);
    return row;
  }

  @Transactional
  public RecipientReputationCalculator.Calculation recalculate(GlobalRecipientEntity entity) {
    List<RecipientFraudEvidence> rows =
        evidence.findByRecipientEntityIdOrderByObservedAtDesc(entity.getId());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    RecipientReputationCalculator.Calculation calculation =
        calculator.calculate(
            rows,
            entity.getReputationStatus(),
            now,
            properties.getEvidenceHalfLifeDays());
    entity.setReputationCategory(calculation.category());
    entity.setReputationScore(calculation.score());
    entity.setEvidenceCount(calculation.evidenceCount());
    entity.setReportCount(calculation.reportCount());
    entity.setDistinctReporterCount(calculation.distinctReporterCount());
    entity.setLastEvidenceAt(calculation.lastEvidenceAt());
    entity.setLastCalculatedAt(now);
    entity.setCalculationVersion(calculation.version());
    entities.save(entity);

    RecipientReputationSnapshot snapshot = new RecipientReputationSnapshot();
    snapshot.setRecipientEntity(entity);
    snapshot.setScore(calculation.score());
    snapshot.setCategory(calculation.category());
    snapshot.setStatus(entity.getReputationStatus());
    snapshot.setEvidenceCount(calculation.evidenceCount());
    snapshot.setReportCount(calculation.reportCount());
    snapshot.setDistinctReporterCount(calculation.distinctReporterCount());
    snapshot.setCalculatedAt(now);
    snapshot.setCalculationVersion(calculation.version());
    snapshot.setContributingEvidence(
        "version="
            + calculation.version()
            + ";evidence="
            + calculation.evidenceCount()
            + ";reports="
            + calculation.reportCount());
    snapshots.save(snapshot);
    return calculation;
  }

  @Transactional
  public void revokeEvidenceForReport(FraudIntelligenceReport report, String reason) {
    evidence
        .findByRecipientEntityIdOrderByObservedAtDesc(report.getRecipientEntity().getId())
        .stream()
        .filter(row -> row.getSourceReport() != null)
        .filter(row -> row.getSourceReport().getId().equals(report.getId()))
        .forEach(
            row -> {
              row.setVerificationStatus(EvidenceVerificationStatus.REJECTED);
              row.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
              row.setRevokedReason(reason);
              evidence.save(row);
            });
  }

  @Transactional
  public void applyStatus(GlobalRecipientEntity entity, ReputationStatus target) {
    if (target == null || entity.getReputationStatus() == target) {
      return;
    }
    if (!entity.getReputationStatus().canTransitionTo(target)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Invalid reputation transition "
              + entity.getReputationStatus()
              + " -> "
              + target);
    }
    entity.setReputationStatus(target);
    entities.save(entity);
  }

  private void advanceStatus(
      GlobalRecipientEntity entity,
      EvidenceSource source,
      EvidenceVerificationStatus verification) {
    ReputationStatus current = entity.getReputationStatus();
    ReputationStatus target = targetFor(current, source, verification);
    if (target != current && current.canTransitionTo(target)) {
      entity.setReputationStatus(target);
    }
  }

  private static ReputationStatus targetFor(
      ReputationStatus current,
      EvidenceSource source,
      EvidenceVerificationStatus verification) {
    if (current == ReputationStatus.CONFIRMED_FRAUD) {
      return current;
    }
    if (current == ReputationStatus.CLEARED) {
      boolean verified =
          verification == EvidenceVerificationStatus.VERIFIED
              || (source != null && source.reliability() >= 0.9);
      return verified ? ReputationStatus.UNDER_REVIEW : current;
    }
    if (current == ReputationStatus.UNKNOWN) {
      return source == EvidenceSource.USER_REPORT
          ? ReputationStatus.REPORTED
          : ReputationStatus.UNDER_REVIEW;
    }
    if (current == ReputationStatus.REPORTED) {
      return source != null && source.reliability() >= 0.5
          ? ReputationStatus.UNDER_REVIEW
          : current;
    }
    if (current == ReputationStatus.EXPIRED) {
      return ReputationStatus.UNDER_REVIEW;
    }
    return current;
  }

  private static BigDecimal clamp(double confidence) {
    double value = Math.max(0.0, Math.min(confidence, 1.0));
    return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
  }
}
