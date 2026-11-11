package com.guardpay.risk;

/**
 * Centralized score thresholds and level/action mapping.
 *
 * <p>Thresholds: 0–39 LOW, 40–69 MEDIUM, 70–84 HIGH, 85–100 CRITICAL.
 * Actions: LOW → ALLOW, MEDIUM/HIGH → VERIFY, CRITICAL → STOP.
 * The mapping is pure and deterministic — no LLM can override it.
 */
public final class RiskLevels {

  /** Highest possible score; the engine caps totals with {@code min(total, MAX_SCORE)}. */
  public static final int MAX_SCORE = 100;

  public static final int LOW_MAX = 39;
  public static final int MEDIUM_MAX = 69;
  public static final int HIGH_MAX = 84;

  private RiskLevels() {}

  public static RiskLevel levelFor(int score) {
    return levelFor(score, RiskCalibration.defaults());
  }

  /** Applies a deployed calibration. The scoring itself is unchanged. */
  public static RiskLevel levelFor(int score, RiskCalibration calibration) {
    return RiskCalibration.requireValid(calibration).levelFor(score);
  }

  public static RecommendedAction actionFor(RiskLevel level) {
    return switch (level) {
      case LOW -> RecommendedAction.ALLOW;
      case MEDIUM, HIGH -> RecommendedAction.VERIFY;
      case CRITICAL -> RecommendedAction.STOP;
    };
  }
}
