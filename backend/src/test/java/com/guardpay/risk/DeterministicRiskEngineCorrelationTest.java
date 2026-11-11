package com.guardpay.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.correlation.CorrelationResult;
import com.guardpay.risk.correlation.RiskCorrelationEngine;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicRiskEngineCorrelationTest {

  private final DeterministicRiskEngine engine = new DeterministicRiskEngine();

  @Test
  void correlationRaisesScoreAndLevelWithoutChangingTheActionMapping() {
    CorrelationResult correlation = accountTakeoverCorrelation();
    RiskEvaluationRequest request = req(1, 5, 10, 11);

    RiskEvaluationResult base = engine.evaluate(request);
    RiskEvaluationResult withCorrelation = engine.evaluate(request, correlation);

    assertEquals(60, base.score());
    assertEquals(RiskLevel.MEDIUM, base.riskLevel());
    assertEquals(base.score() + correlation.contributionPoints(), withCorrelation.score());
    assertEquals(RiskLevel.HIGH, withCorrelation.riskLevel());
    assertEquals(RecommendedAction.VERIFY, withCorrelation.recommendedAction());
    assertEquals(correlation.contributionPoints(), withCorrelation.correlationContribution());
    assertEquals(1, withCorrelation.correlationFindings().size());
    assertTrue(withCorrelation.explanation().contains("Correlated signals"));
  }

  @Test
  void emptyCorrelationIsEvidenceNeutral() {
    RiskEvaluationRequest request = req(1, 10, 11);

    RiskEvaluationResult base = engine.evaluate(request);
    RiskEvaluationResult withEmpty = engine.evaluate(request, CorrelationResult.empty("NO_MATCH"));

    assertEquals(base.score(), withEmpty.score());
    assertEquals(base.riskLevel(), withEmpty.riskLevel());
    assertEquals(base.recommendedAction(), withEmpty.recommendedAction());
    assertEquals(0, withEmpty.correlationContribution());
    assertTrue(withEmpty.correlationFindings().isEmpty());
  }

  @Test
  void correlationNeverLowersTheScore() {
    RiskEvaluationRequest request = req(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        17, 18, 19, 20, 21, 22, 23, 24, 25);
    CorrelationResult correlation = accountTakeoverCorrelation();

    RiskEvaluationResult base = engine.evaluate(request);
    RiskEvaluationResult withCorrelation = engine.evaluate(request, correlation);

    assertTrue(withCorrelation.score() >= base.score());
    assertTrue(withCorrelation.score() <= RiskLevels.MAX_SCORE);
  }

  @Test
  void scoreStaysCappedAtOneHundred() {
    RiskEvaluationRequest request =
        req(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25);

    RiskEvaluationResult result = engine.evaluate(request, accountTakeoverCorrelation());

    assertEquals(RiskLevels.MAX_SCORE, result.score());
    assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    assertEquals(RecommendedAction.STOP, result.recommendedAction());
  }

  @Test
  void hardBlockStillForcesStopWithCorrelationEvidence() {
    RiskEvaluationRequest request = req(16);

    RiskEvaluationResult result = engine.evaluate(request, accountTakeoverCorrelation());

    assertEquals(RiskLevels.MAX_SCORE, result.score());
    assertEquals(RecommendedAction.STOP, result.recommendedAction());
    assertEquals(1, result.correlationFindings().size());
  }

  @Test
  void fiveArgumentResultConstructorRemainsValid() {
    RiskEvaluationResult result =
        new RiskEvaluationResult(0, RiskLevel.LOW, RecommendedAction.ALLOW, List.of(), "Safe.");

    assertEquals(0, result.correlationContribution());
    assertTrue(result.correlationFindings().isEmpty());
  }

  private static CorrelationResult accountTakeoverCorrelation() {
    return new RiskCorrelationEngine()
        .evaluate(
            List.of(
                new PaymentSignal(
                    PaymentSignalType.NEW_DEVICE,
                    true,
                    SignalSource.TRANSACTION_HISTORY,
                    null,
                    "New device."),
                new PaymentSignal(
                    PaymentSignalType.NEW_GEO,
                    true,
                    SignalSource.TRANSACTION_HISTORY,
                    null,
                    "New country."),
                new PaymentSignal(
                    PaymentSignalType.NEW_RECIPIENT,
                    true,
                    SignalSource.TRANSACTION_HISTORY,
                    null,
                    "New recipient.")));
  }

  private static RiskEvaluationRequest req(int... trueFlags) {
    return req(new BigDecimal("45"), new BigDecimal("87"), trueFlags);
  }

  private static RiskEvaluationRequest req(
      BigDecimal amount, BigDecimal average, int... trueFlags) {
    boolean[] flags = new boolean[26];
    for (int flag : trueFlags) {
      if (flag >= 0 && flag < 26) {
        flags[flag] = true;
      }
    }
    return new RiskEvaluationRequest(
        amount,
        average,
        flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6], flags[7],
        flags[8], flags[9], flags[10], flags[11], flags[12], flags[13], flags[14],
        flags[15], flags[16], flags[17], flags[18], flags[19], flags[20], flags[21],
        flags[22], flags[23], flags[24], flags[25]);
  }
}
