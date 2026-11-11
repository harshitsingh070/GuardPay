package com.guardpay.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskCalibrationTest {

  private final DeterministicRiskEngine engine = new DeterministicRiskEngine();

  @Test
  void defaultsPreserveExistingBoundaries() {
    RiskCalibration calibration = RiskCalibration.defaults();

    assertEquals(40, calibration.verifyThreshold());
    assertEquals(85, calibration.stopThreshold());
    assertEquals(RiskLevel.LOW, calibration.levelFor(39));
    assertEquals(RiskLevel.MEDIUM, calibration.levelFor(40));
    assertEquals(RiskLevel.MEDIUM, calibration.levelFor(69));
    assertEquals(RiskLevel.HIGH, calibration.levelFor(70));
    assertEquals(RiskLevel.CRITICAL, calibration.levelFor(85));
  }

  @Test
  void deployedThresholdsMoveVerifyAndStopBoundaries() {
    RiskCalibration calibration =
        RiskCalibration.ofThresholds(30, 80, "calib-v1-test", "dataset-v1-test");

    assertEquals(RiskLevel.LOW, calibration.levelFor(29));
    assertEquals(RiskLevel.MEDIUM, calibration.levelFor(30));
    assertEquals(RiskLevel.CRITICAL, calibration.levelFor(80));
  }

  @Test
  void engineUsesCalibrationForLevelButNotForScore() {
    RiskEvaluationRequest request = req(0, 1);
    RiskEvaluationResult defaultResult = engine.evaluate(request);
    RiskEvaluationResult calibrated =
        engine.evaluate(
            request, null, null, RiskCalibration.ofThresholds(30, 80, "calib", "dataset"));

    assertEquals(defaultResult.score(), calibrated.score());
    assertEquals(30, calibrated.score());
    assertEquals(RiskLevel.LOW, defaultResult.riskLevel());
    assertEquals(RiskLevel.MEDIUM, calibrated.riskLevel());
  }

  @Test
  void hardBlockStaysStopUnderAnyCalibration() {
    RiskEvaluationRequest request = req(16);
    RiskEvaluationResult result =
        engine.evaluate(
            request, null, null, RiskCalibration.ofThresholds(99, 100, "calib", "dataset"));

    assertEquals(100, result.score());
    assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    assertEquals(RecommendedAction.STOP, result.recommendedAction());
  }

  private static RiskEvaluationRequest req(int... trueFlags) {
    boolean[] flags = new boolean[26];
    for (int flag : trueFlags) {
      flags[flag] = true;
    }
    return new RiskEvaluationRequest(
        new BigDecimal("45"),
        new BigDecimal("87"),
        flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6], flags[7],
        flags[8], flags[9], flags[10], flags[11], flags[12], flags[13], flags[14],
        flags[15], flags[16], flags[17], flags[18], flags[19], flags[20], flags[21],
        flags[22], flags[23], flags[24], flags[25]);
  }

  @Test
  void invalidBandsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new RiskCalibration(70, 60, 80, "x", null));
    assertThrows(
        IllegalArgumentException.class, () -> RiskCalibration.ofThresholds(0, 80, "x", null));
  }
}
