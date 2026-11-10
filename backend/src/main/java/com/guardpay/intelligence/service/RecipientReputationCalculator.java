package com.guardpay.intelligence.service;

import com.guardpay.intelligence.domain.EvidenceSource;
import com.guardpay.intelligence.domain.EvidenceVerificationStatus;
import com.guardpay.intelligence.domain.RecipientFraudEvidence;
import com.guardpay.intelligence.domain.ReputationCategory;
import com.guardpay.intelligence.domain.ReputationStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic reputation scoring.
 *
 * <p>Each evidence observation contributes
 * {@code reliability * confidence * recency}. Observations that describe the
 * same underlying fact (same reporter, transaction, or source) are discounted
 * before aggregation, and the aggregate uses a probabilistic-OR so ten copies
 * of one report cannot become ten independent facts.
 */
public final class RecipientReputationCalculator {

  public static final String CALCULATION_VERSION = "reputation-v1";
  public static final int MAX_SCORE = 100;
  static final double MAX_SINGLE_WEIGHT = 0.95;
  static final double GROUP_DECAY = 0.5;
  static final double MIN_RECENCY = 0.1;

  public Calculation calculate(
      List<RecipientFraudEvidence> evidence,
      ReputationStatus status,
      OffsetDateTime now,
      int halfLifeDays) {
    List<RecipientFraudEvidence> usable = usableEvidence(evidence);
    Map<String, List<Double>> groups = new LinkedHashMap<>();
    int reportCount = 0;
    Set<String> reporters = new HashSet<>();
    OffsetDateTime lastEvidenceAt = null;

    int safeHalfLife = Math.max(1, halfLifeDays);
    OffsetDateTime asOf = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;

    for (RecipientFraudEvidence row : usable) {
      double weight = weightOf(row, asOf, safeHalfLife);
      groups.computeIfAbsent(groupKey(row), ignored -> new ArrayList<>()).add(weight);
      if (row.getSource() == EvidenceSource.USER_REPORT) {
        reportCount += Math.max(1, row.getReportCount());
        if (row.getReporterGroupKey() != null) {
          reporters.add(row.getReporterGroupKey());
        }
      }
      OffsetDateTime observed =
          row.getLastObservedAt() != null ? row.getLastObservedAt() : row.getObservedAt();
      if (observed != null && (lastEvidenceAt == null || observed.isAfter(lastEvidenceAt))) {
        lastEvidenceAt = observed;
      }
    }

    double aggregate = 0.0;
    for (List<Double> weights : groups.values()) {
      weights.sort(Comparator.reverseOrder());
      double groupContribution = 0.0;
      for (int i = 0; i < weights.size(); i++) {
        groupContribution += weights.get(i) / (1.0 + GROUP_DECAY * i);
      }
      aggregate = 1.0 - (1.0 - aggregate) * (1.0 - Math.min(groupContribution, MAX_SINGLE_WEIGHT));
    }

    int score = (int) Math.round(MAX_SCORE * aggregate);
    score = Math.max(0, Math.min(score, MAX_SCORE));

    ReputationStatus effectiveStatus =
        status == null ? ReputationStatus.UNKNOWN : status;
    if (effectiveStatus == ReputationStatus.CLEARED) {
      score = 0;
    } else if (effectiveStatus == ReputationStatus.CONFIRMED_FRAUD) {
      score = MAX_SCORE;
    }

    ReputationCategory category = categoryOf(effectiveStatus, score, usable.size());
    return new Calculation(
        score,
        category,
        usable.size(),
        reportCount,
        reporters.size(),
        lastEvidenceAt,
        CALCULATION_VERSION);
  }

  private static List<RecipientFraudEvidence> usableEvidence(
      List<RecipientFraudEvidence> evidence) {
    if (evidence == null) {
      return List.of();
    }
    return evidence.stream()
        .filter(Objects::nonNull)
        .filter(row -> row.getRevokedAt() == null)
        .filter(
            row ->
                row.getVerificationStatus() != EvidenceVerificationStatus.REJECTED
                    && row.getVerificationStatus() != EvidenceVerificationStatus.REVOKED)
        .toList();
  }

  private static double weightOf(
      RecipientFraudEvidence row, OffsetDateTime now, int halfLifeDays) {
    double reliability =
        row.getSource() == null ? 0.0 : row.getSource().reliability();
    double confidence = confidenceOf(row.getConfidence());
    OffsetDateTime observed =
        row.getLastObservedAt() != null ? row.getLastObservedAt() : row.getObservedAt();
    double ageDays =
        observed == null
            ? 0.0
            : Math.max(0.0, Duration.between(observed, now).toMinutes() / (double) (60 * 24));
    double recency = Math.max(MIN_RECENCY, Math.pow(0.5, ageDays / halfLifeDays));
    return reliability * confidence * recency;
  }

  private static double confidenceOf(BigDecimal confidence) {
    if (confidence == null) {
      return 0.5;
    }
    double value = confidence.doubleValue();
    return Math.max(0.0, Math.min(value, 1.0));
  }

  private static String groupKey(RecipientFraudEvidence row) {
    if (row.getSource() == EvidenceSource.USER_REPORT) {
      return row.getReporterGroupKey() != null
          ? "reporter:" + row.getReporterGroupKey()
          : "report:" + row.getEvidenceFingerprint();
    }
    if (row.getRelatedTransactionId() != null) {
      return "transaction:" + row.getRelatedTransactionId();
    }
    return "source:" + (row.getSource() == null ? "UNKNOWN" : row.getSource().name());
  }

  private static ReputationCategory categoryOf(
      ReputationStatus status, int score, int evidenceCount) {
    if (status == ReputationStatus.CONFIRMED_FRAUD) {
      return ReputationCategory.CONFIRMED_FRAUD;
    }
    if (status == ReputationStatus.CLEARED) {
      return ReputationCategory.CLEARED;
    }
    if (evidenceCount == 0 || score == 0) {
      return ReputationCategory.NO_DATA;
    }
    if (score >= 60) {
      return ReputationCategory.HIGH_RISK;
    }
    if (score >= 25) {
      return ReputationCategory.ELEVATED_RISK;
    }
    return ReputationCategory.LOW_RISK_EVIDENCE;
  }

  /** Immutable result of one reputation calculation. */
  public record Calculation(
      int score,
      ReputationCategory category,
      int evidenceCount,
      int reportCount,
      int distinctReporterCount,
      OffsetDateTime lastEvidenceAt,
      String version) {}
}
