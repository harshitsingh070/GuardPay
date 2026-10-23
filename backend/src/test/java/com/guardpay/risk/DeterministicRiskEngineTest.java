package com.guardpay.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link DeterministicRiskEngine}. Pure logic — no Spring
 * context, no database. Covers every case in the milestone spec.
 */
class DeterministicRiskEngineTest {

  private final DeterministicRiskEngine engine = new DeterministicRiskEngine();

  private static RiskEvaluationRequest base() {
    return new RiskEvaluationRequest(
        new BigDecimal("45"), new BigDecimal("87"),
        false, false, false, false, false, false, false, false);
  }

  private static Map<String, Boolean> triggeredBy(RiskEvaluationResult result) {
    return result.findings().stream()
        .collect(Collectors.toMap(RiskFinding::code, RiskFinding::triggered));
  }

  @Test
  void safePaymentScoresZero() {
    RiskEvaluationResult result = engine.evaluate(base());

    assertEquals(0, result.score());
    assertEquals(RiskLevel.LOW, result.riskLevel());
    assertEquals(RecommendedAction.ALLOW, result.recommendedAction());
    assertEquals(10, result.findings().size());
    assertTrue(triggeredBy(result).values().stream().noneMatch(b -> b));
    assertTrue(result.explanation() != null && !result.explanation().isBlank());
  }

  @Test
  void newRecipientAddsTwenty() {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("45"), new BigDecimal("87"),
            false, true, false, false, false, false, false, false);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(20, result.score());
    assertEquals(RiskLevel.LOW, result.riskLevel());
    assertEquals(RecommendedAction.ALLOW, result.recommendedAction());
    assertTrue(triggeredBy(result).get("NEW_RECIPIENT"));
  }

  @Test
  void repeatedAttemptsAddFifteen() {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("45"), new BigDecimal("87"),
            false, false, true, false, false, false, false, false);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(15, result.score());
    assertTrue(triggeredBy(result).get("REPEATED_ATTEMPTS"));
  }

  @Test
  void activeCallAddsFifteen() {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("45"), new BigDecimal("87"),
            true, false, false, false, false, false, false, false);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(15, result.score());
    assertTrue(triggeredBy(result).get("ACTIVE_CALL"));
  }

  @Test
  void largeAmountTriggersAtFiveTimesAverage() {
    // 500 >= 87 * 5 (435) -> fires; 434 would not.
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("500"), new BigDecimal("87"),
            false, false, false, false, false, false, false, false);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(20, result.score());
    assertTrue(triggeredBy(result).get("LARGE_AMOUNT"));

    RiskEvaluationRequest justBelow =
        new RiskEvaluationRequest(
            new BigDecimal("434"), new BigDecimal("87"),
            false, false, false, false, false, false, false, false);
    assertFalse(triggeredBy(engine.evaluate(justBelow)).get("LARGE_AMOUNT"));
  }

  @Test
  void multipleSignalsSumToSixty() {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("45"), new BigDecimal("87"),
            true, true, false, false, true, true, false, false);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(60, result.score());
    assertEquals(RiskLevel.MEDIUM, result.riskLevel());
    assertEquals(RecommendedAction.VERIFY, result.recommendedAction());
  }

  @Test
  void heroScenarioCapsAtOneHundredCriticalStop() {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("2499"), new BigDecimal("87"),
            true, true, true, true, true, true, true, true, true);

    RiskEvaluationResult result = engine.evaluate(request);

    // Raw total is 155; the final score must be capped at 100.
    assertEquals(100, result.score());
    assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    assertEquals(RecommendedAction.STOP, result.recommendedAction());
    assertTrue(triggeredBy(result).values().stream().allMatch(b -> b));
  }

  @Test
  void scoreNeverExceedsOneHundred() {
    RiskEvaluationRequest everything =
        new RiskEvaluationRequest(
            new BigDecimal("999999"), new BigDecimal("1"),
            true, true, true, true, true, true, true, true);

    assertTrue(engine.evaluate(everything).score() <= 100);
  }

  @ParameterizedTest(name = "{0} -> {1}/{2}")
  @CsvSource({
    "39, LOW, ALLOW",
    "40, MEDIUM, VERIFY",
    "69, MEDIUM, VERIFY",
    "70, HIGH, VERIFY",
    "84, HIGH, VERIFY",
    "85, CRITICAL, STOP",
    "100, CRITICAL, STOP"
  })
  void levelAndActionBoundaries(int score, RiskLevel level, RecommendedAction action) {
    assertEquals(level, RiskLevels.levelFor(score));
    assertEquals(action, RiskLevels.actionFor(level));
  }

  @Test
  void reachableSeventyIsHigh() {
    // 20 (new) + 20 (large: 500 >= 87*5) + 15 (call) + 15 (invoice) = 70.
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("500"), new BigDecimal("87"),
            true, true, false, false, true, false, false, false);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(70, result.score());
    assertEquals(RiskLevel.HIGH, result.riskLevel());
    assertEquals(RecommendedAction.VERIFY, result.recommendedAction());
  }

  @Test
  void missingHistorySkipsLargeAmountButKeepsOtherSignals() {
    List<BigDecimal> badAverages = Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-5"));
    for (BigDecimal average : badAverages) {
      RiskEvaluationRequest request =
          new RiskEvaluationRequest(
              new BigDecimal("9999"), average,
              true, false, false, false, false, false, false, false);

      RiskEvaluationResult result = engine.evaluate(request);

      assertFalse(triggeredBy(result).get("LARGE_AMOUNT"));
      assertEquals(15, result.score());
    }
  }

  @Test
  void missingContextAddsTwenty() {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("2499"), new BigDecimal("87"),
            false, true, false, false, false, false, false, false, true);

    RiskEvaluationResult result = engine.evaluate(request);

    // 20 new recipient + 20 large amount + 20 missing context.
    assertEquals(60, result.score());
    assertEquals(RiskLevel.MEDIUM, result.riskLevel());
    assertEquals(RecommendedAction.VERIFY, result.recommendedAction());
    assertTrue(triggeredBy(result).get("MISSING_CONTEXT"));
  }

  @Test
  void identicalInputGivesIdenticalOutput() {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            new BigDecimal("800"), new BigDecimal("87"),
            false, true, false, false, true, false, true, false);

    assertEquals(engine.evaluate(request), engine.evaluate(request));
  }

  @Test
  void findingsKeepStableSignalOrder() {
    List<String> codes =
        engine.evaluate(base()).findings().stream().map(RiskFinding::code).toList();

    assertEquals(
        List.of(
            "ACTIVE_CALL",
            "NEW_RECIPIENT",
            "LARGE_AMOUNT",
            "REPEATED_ATTEMPTS",
            "UNKNOWN_MERCHANT",
            "UNEXPECTED_INVOICE",
            "URGENT_LANGUAGE",
            "SUSPICIOUS_URL",
            "SOCIAL_ENGINEERING",
            "MISSING_CONTEXT"),
        codes);
  }
}
