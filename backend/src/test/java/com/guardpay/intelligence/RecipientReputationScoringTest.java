package com.guardpay.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.intelligence.domain.EvidenceSource;
import com.guardpay.intelligence.domain.EvidenceType;
import com.guardpay.intelligence.domain.IdentifierMatchLevel;
import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.RecipientFraudEvidence;
import com.guardpay.intelligence.domain.ReputationCategory;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.service.RecipientReputationCalculator;
import com.guardpay.intelligence.service.RecipientReputationPoints;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipientReputationScoringTest {

  private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  private final RecipientReputationCalculator calculator = new RecipientReputationCalculator();

  @Test
  void pointsAreScaledByMatchLevelAndIdentityConfidence() {
    assertEquals(0, RecipientReputationPoints.points(ReputationStatus.UNKNOWN, null, null));
    assertEquals(
        10,
        RecipientReputationPoints.points(
            ReputationStatus.REPORTED,
            IdentifierMatchLevel.EXACT_VERIFIED_MATCH,
            IdentityConfidence.VERY_HIGH));
    assertTrue(
        RecipientReputationPoints.points(
                ReputationStatus.REPORTED,
                IdentifierMatchLevel.POSSIBLE_MATCH,
                IdentityConfidence.LOW)
            < 10);
    assertEquals(
        RecipientReputationPoints.MAX_POINTS,
        RecipientReputationPoints.points(
            ReputationStatus.CONFIRMED_FRAUD,
            IdentifierMatchLevel.EXACT_VERIFIED_MATCH,
            IdentityConfidence.VERY_HIGH));
  }

  @Test
  void duplicateReportsFromOneReporterAreDiscounted() {
    List<RecipientFraudEvidence> oneReporter =
        List.of(userReport("reporter-a", "fp-1"), userReport("reporter-a", "fp-2"));
    List<RecipientFraudEvidence> twoReporters =
        List.of(userReport("reporter-a", "fp-1"), userReport("reporter-b", "fp-2"));

    RecipientReputationCalculator.Calculation discounted =
        calculator.calculate(oneReporter, ReputationStatus.REPORTED, NOW, 180);
    RecipientReputationCalculator.Calculation independent =
        calculator.calculate(twoReporters, ReputationStatus.REPORTED, NOW, 180);

    assertEquals(2, discounted.reportCount());
    assertEquals(1, discounted.distinctReporterCount());
    assertEquals(2, independent.distinctReporterCount());
    assertTrue(discounted.score() <= independent.score());
  }

  @Test
  void clearedIsZeroAndConfirmedFraudIsMaximum() {
    List<RecipientFraudEvidence> evidence = List.of(userReport("reporter-a", "fp-1"));

    assertEquals(
        0,
        calculator.calculate(evidence, ReputationStatus.CLEARED, NOW, 180).score());
    assertEquals(
        RecipientReputationCalculator.MAX_SCORE,
        calculator.calculate(evidence, ReputationStatus.CONFIRMED_FRAUD, NOW, 180).score());
    assertEquals(
        ReputationCategory.CONFIRMED_FRAUD,
        calculator
            .calculate(evidence, ReputationStatus.CONFIRMED_FRAUD, NOW, 180)
            .category());
  }

  @Test
  void rejectedEvidenceIsIgnored() {
    RecipientFraudEvidence rejected = userReport("reporter-a", "fp-1");
    rejected.setVerificationStatus(
        com.guardpay.intelligence.domain.EvidenceVerificationStatus.REJECTED);

    RecipientReputationCalculator.Calculation calculation =
        calculator.calculate(List.of(rejected), ReputationStatus.REPORTED, NOW, 180);

    assertEquals(0, calculation.evidenceCount());
    assertEquals(0, calculation.score());
    assertEquals(ReputationCategory.NO_DATA, calculation.category());
  }

  private static RecipientFraudEvidence userReport(String reporterGroup, String fingerprint) {
    RecipientFraudEvidence row = new RecipientFraudEvidence();
    row.setEvidenceType(EvidenceType.RECIPIENT_REPORTED);
    row.setSource(EvidenceSource.USER_REPORT);
    row.setConfidence(BigDecimal.ONE);
    row.setEvidenceFingerprint(fingerprint);
    row.setReporterGroupKey(reporterGroup);
    row.setObservedAt(NOW);
    row.setLastObservedAt(NOW);
    row.setReportCount(1);
    return row;
  }
}
