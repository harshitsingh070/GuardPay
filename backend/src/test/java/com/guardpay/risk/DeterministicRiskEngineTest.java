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
 * Unit tests for {@link DeterministicRiskEngine}. Pure logic â€” no Spring
 * context, no database. Covers every case in the milestone spec.
 */
class DeterministicRiskEngineTest {

  private final DeterministicRiskEngine engine = new DeterministicRiskEngine();

  // Boolean positions: 0=activeCall, 1=newRecipient, 2=repeatedAttempts,
  // 3=unknownMerchant, 4=unexpectedInvoice, 5=urgentLanguage,
  // 6=suspiciousUrl, 7=socialEngineeringDetected, 8=missingContext,
  // 9=recipientAmountAnomaly, 10=newDevice, 11=newGeo, 12=impossibleTravel,
  // 13=vpnProxy, 14=hourlyVelocity, 15=dailyVelocity,
  // 16=blocklistedRecipient, 17=knownRecipientScam, 18=splitPayment,
  // 19=amountDeviation, 20=unusualPaymentHour,
  // 21=recipientBehaviorDeviation, 22=transactionFrequencyAnomaly,
  // 23=geographicBehaviorDeviation, 24=deviceBehaviorDeviation,
  // 25=overallBehaviorDeviation

  private static RiskEvaluationRequest req(int... trueFlags) {
    return req(new BigDecimal("45"), new BigDecimal("87"), trueFlags);
  }

  private static RiskEvaluationRequest req(
      BigDecimal amount, BigDecimal average, int... trueFlags) {
    boolean[] flags = new boolean[26];
    for (int f : trueFlags) {
      if (f >= 0 && f < 26) {
        flags[f] = true;
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

  private static Map<String, Boolean> triggeredBy(RiskEvaluationResult result) {
    return result.findings().stream()
        .collect(Collectors.toMap(RiskFinding::code, RiskFinding::triggered));
  }

  @Test
  void safePaymentScoresZero() {
    RiskEvaluationResult result = engine.evaluate(req());

    assertEquals(0, result.score());
    assertEquals(RiskLevel.LOW, result.riskLevel());
    assertEquals(RecommendedAction.ALLOW, result.recommendedAction());
    assertEquals(33, result.findings().size());
    assertTrue(triggeredBy(result).values().stream().noneMatch(b -> b));
    assertTrue(result.explanation() != null && !result.explanation().isBlank());
  }

  @Test
  void blocklistedRecipientIsHardBlockedEvenWithoutOtherSignals() {
    RiskEvaluationRequest request = req(16); // blocklistedRecipient

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(100, result.score());
    assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    assertEquals(RecommendedAction.STOP, result.recommendedAction());
    assertTrue(triggeredBy(result).get("BLOCKLISTED_RECIPIENT"));
  }

  @Test
  void newRecipientAddsFifteen() {
    RiskEvaluationRequest request = req(1); // newRecipient

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(15, result.score());
    assertEquals(RiskLevel.LOW, result.riskLevel());
    assertEquals(RecommendedAction.ALLOW, result.recommendedAction());
    assertTrue(triggeredBy(result).get("NEW_RECIPIENT"));
  }

  @Test
  void repeatedAttemptsAddFifteen() {
    RiskEvaluationRequest request = req(2); // repeatedAttempts

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(15, result.score());
    assertTrue(triggeredBy(result).get("REPEATED_ATTEMPTS"));
  }

  @Test
  void activeCallAddsFifteen() {
    RiskEvaluationRequest request = req(0); // activeCall

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(15, result.score());
    assertTrue(triggeredBy(result).get("ACTIVE_CALL"));
  }

  @Test
  void largeAmountTriggersAtFiveTimesAverage() {
    RiskEvaluationRequest request = req(new BigDecimal("500"), new BigDecimal("87"));

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(20, result.score());
    assertTrue(triggeredBy(result).get("LARGE_AMOUNT"));

    RiskEvaluationRequest justBelow = req(new BigDecimal("434"), new BigDecimal("87"));
    assertFalse(triggeredBy(engine.evaluate(justBelow)).get("LARGE_AMOUNT"));
  }

  @Test
  void recipientAmountAnomalyAddsTwenty() {
    RiskEvaluationRequest request = req(new BigDecimal("45"), new BigDecimal("87"), 9);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(20, result.score());
    assertTrue(triggeredBy(result).get("RECIPIENT_AMOUNT_ANOMALY"));
  }

  @Test
  void multipleSignalsSumToFiftyFive() {
    // activeCall(0) + newRecipient(1) + unexpectedInvoice(4) + urgentLanguage(5) = 15+15+15+10 = 55
    RiskEvaluationRequest request = req(0, 1, 4, 5);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(55, result.score());
    assertEquals(RiskLevel.MEDIUM, result.riskLevel());
    assertEquals(RecommendedAction.VERIFY, result.recommendedAction());
  }

  @Test
  void heroScenarioCapsAtOneHundredCriticalStop() {
    RiskEvaluationRequest request =
        req(
            new BigDecimal("2499"),
            new BigDecimal("87"),
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(100, result.score());
    assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    assertEquals(RecommendedAction.STOP, result.recommendedAction());
    assertEquals(33, result.findings().size());
  }

  @Test
  void scoreNeverExceedsOneHundred() {
    RiskEvaluationRequest everything =
        req(
            new BigDecimal("999999"),
            new BigDecimal("1"),
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25);

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
  void reachableSixtyFiveIsHigh() {
    // 15 (new) + 20 (large: 500 >= 87*5) + 15 (call) + 15 (invoice) = 65.
    RiskEvaluationRequest request = req(new BigDecimal("500"), new BigDecimal("87"), 0, 1, 4);

    RiskEvaluationResult result = engine.evaluate(request);

    assertEquals(65, result.score());
    assertEquals(RiskLevel.HIGH, result.riskLevel());
    assertEquals(RecommendedAction.VERIFY, result.recommendedAction());
  }

  @Test
  void missingHistorySkipsLargeAmountButKeepsOtherSignals() {
    List<BigDecimal> badAverages = Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-5"));
    for (BigDecimal average : badAverages) {
      RiskEvaluationRequest request = req(new BigDecimal("9999"), average, 0);

      RiskEvaluationResult result = engine.evaluate(request);

      assertFalse(triggeredBy(result).get("LARGE_AMOUNT"));
      assertEquals(15, result.score());
    }
  }

  @Test
  void missingContextAddsTwenty() {
    // missingContext is at index 8; amount=2499 also fires LARGE_AMOUNT.
    RiskEvaluationRequest request =
        req(new BigDecimal("2499"), new BigDecimal("87"), 1, 8);

    RiskEvaluationResult result = engine.evaluate(request);

    // 15 new recipient + 20 large amount + 20 missing context.
    assertEquals(55, result.score());
    assertEquals(RiskLevel.MEDIUM, result.riskLevel());
    assertEquals(RecommendedAction.VERIFY, result.recommendedAction());
    assertTrue(triggeredBy(result).get("MISSING_CONTEXT"));
  }

  @Test
  void identicalInputGivesIdenticalOutput() {
    RiskEvaluationRequest request = req(new BigDecimal("800"), new BigDecimal("87"), 1, 4, 6);

    assertEquals(engine.evaluate(request), engine.evaluate(request));
  }

  @Test
  void findingsKeepStableSignalOrder() {
    List<String> codes =
        engine.evaluate(req()).findings().stream().map(RiskFinding::code).toList();

    assertEquals(
        List.of(
            "ACTIVE_CALL",
            "NEW_RECIPIENT",
            "LARGE_AMOUNT",
            "RECIPIENT_AMOUNT_ANOMALY",
            "REPEATED_ATTEMPTS",
            "VELOCITY_HOURLY",
            "VELOCITY_DAILY",
            "BLOCKLISTED_RECIPIENT",
            "UNKNOWN_MERCHANT",
            "UNEXPECTED_INVOICE",
            "URGENT_LANGUAGE",
            "SUSPICIOUS_URL",
            "SOCIAL_ENGINEERING",
            "KNOWN_RECIPIENT_SCAM",
            "SPLIT_PAYMENT",
            "MISSING_CONTEXT",
            "NEW_DEVICE",
            "NEW_GEO",
            "IMPOSSIBLE_TRAVEL",
            "VPN_PROXY",
            "AMOUNT_DEVIATION",
            "UNUSUAL_PAYMENT_HOUR",
            "RECIPIENT_BEHAVIOR_DEVIATION",
            "TRANSACTION_FREQUENCY_ANOMALY",
            "GEOGRAPHIC_BEHAVIOR_DEVIATION",
            "DEVICE_BEHAVIOR_DEVIATION",
            "OVERALL_BEHAVIOR_DEVIATION",
            "FIRST_HIGH_VALUE_PAYMENT",
            "FIRST_HIGH_VALUE_TO_RECIPIENT",
            "FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE",
            "SIGNIFICANT_AMOUNT_DEVIATION",
            "AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM",
            "RAPID_AMOUNT_ESCALATION"),
        codes);
  }
}
