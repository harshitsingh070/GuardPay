package com.guardpay.risk;

import java.util.Objects;

/**
 * Versioned score-to-level calibration.
 *
 * <p>{@link #defaults()} reproduces the production thresholds exactly. A
 * deployed calibration may only move the band boundaries; it never changes how
 * the deterministic engine computes the score.
 */
public record RiskCalibration(
    int lowMax, int mediumMax, int highMax, String modelVersion, String datasetVersion) {

  public RiskCalibration {
    if (lowMax < 0 || mediumMax < lowMax || highMax < mediumMax || highMax >= RiskLevels.MAX_SCORE) {
      throw new IllegalArgumentException(
          "Calibration bands must be ordered and below " + RiskLevels.MAX_SCORE);
    }
  }

  public static RiskCalibration defaults() {
    return new RiskCalibration(
        RiskLevels.LOW_MAX,
        RiskLevels.MEDIUM_MAX,
        RiskLevels.HIGH_MAX,
        "deterministic-default",
        null);
  }

  public static RiskCalibration ofThresholds(
      int verifyThreshold, int stopThreshold, String modelVersion, String datasetVersion) {
    int lowMax = verifyThreshold - 1;
    int highMax = stopThreshold - 1;
    int mediumMax = verifyThreshold + Math.max(0, (stopThreshold - verifyThreshold) / 2) - 1;
    return new RiskCalibration(lowMax, mediumMax, highMax, modelVersion, datasetVersion);
  }

  public RiskLevel levelFor(int score) {
    if (score <= lowMax) {
      return RiskLevel.LOW;
    }
    if (score <= mediumMax) {
      return RiskLevel.MEDIUM;
    }
    if (score <= highMax) {
      return RiskLevel.HIGH;
    }
    return RiskLevel.CRITICAL;
  }

  public int verifyThreshold() {
    return lowMax + 1;
  }

  public int stopThreshold() {
    return highMax + 1;
  }

  public boolean isDefault() {
    return "deterministic-default".equals(modelVersion);
  }

  public static RiskCalibration requireValid(RiskCalibration calibration) {
    return Objects.requireNonNullElseGet(calibration, RiskCalibration::defaults);
  }
}
