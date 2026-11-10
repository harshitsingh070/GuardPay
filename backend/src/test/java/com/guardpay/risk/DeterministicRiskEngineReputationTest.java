package com.guardpay.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.intelligence.domain.IdentifierMatchLevel;
import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.MatchStatus;
import com.guardpay.intelligence.domain.ReputationCategory;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.service.RecipientReputationEvidence;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeterministicRiskEngineReputationTest {

  private final DeterministicRiskEngine engine = new DeterministicRiskEngine();

  @Test
  void reputationAddsBoundedPointsAndAFinding() {
    RiskEvaluationResult base = engine.evaluate(request());
    RecipientReputationEvidence reputation = reputation(ReputationStatus.REPORTED, 10);

    RiskEvaluationResult withReputation = engine.evaluate(request(), null, reputation);

    assertEquals(base.score() + 10, withReputation.score());
    assertEquals(10, withReputation.recipientReputationContribution());
    assertTrue(
        withReputation.findings().stream()
            .anyMatch(
                finding ->
                    finding.triggered()
                        && finding.code().equals("GLOBAL_RECIPIENT_REPORTED")));
  }

  @Test
  void confirmedFraudIsCappedAtFortyPoints() {
    RiskEvaluationResult base = engine.evaluate(request());
    RecipientReputationEvidence reputation = reputation(ReputationStatus.CONFIRMED_FRAUD, 40);

    RiskEvaluationResult result = engine.evaluate(request(), null, reputation);

    assertEquals(Math.min(100, base.score() + 40), result.score());
    assertEquals(40, result.recipientReputationContribution());
  }

  @Test
  void unavailableEvidenceContributesNothing() {
    RiskEvaluationResult base = engine.evaluate(request());
    RecipientReputationEvidence unavailable = RecipientReputationEvidence.unavailable();

    RiskEvaluationResult result = engine.evaluate(request(), null, unavailable);

    assertEquals(base.score(), result.score());
    assertEquals(0, result.recipientReputationContribution());
    assertFalse(
        result.findings().stream()
            .anyMatch(finding -> finding.code().startsWith("GLOBAL_RECIPIENT_")));
  }

  @Test
  void reputationFindingIsEvidenceOnlyAndNeverHardBlocks() {
    RiskEvaluationResult result =
        engine.evaluate(request(), null, reputation(ReputationStatus.UNDER_REVIEW, 15));

    assertTrue(result.recommendedAction() != RecommendedAction.STOP);
  }

  private static RecipientReputationEvidence reputation(ReputationStatus status, int points) {
    return new RecipientReputationEvidence(
        MatchStatus.MATCH_FOUND,
        status,
        ReputationCategory.ELEVATED_RISK,
        80,
        points,
        IdentifierMatchLevel.EXACT_VERIFIED_MATCH,
        IdentityConfidence.VERY_HIGH,
        2,
        2,
        "test");
  }

  private static RiskEvaluationRequest request() {
    return new RiskEvaluationRequest(
        new BigDecimal("45"),
        new BigDecimal("50"),
        false, true, false, false, false, false, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false, false, false,
        false, false);
  }
}
