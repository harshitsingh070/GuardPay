package com.guardpay.evaluation;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.RiskLevels;
import java.util.List;

/**
 * Benchmark-only score-to-action policy. It deliberately mirrors the production
 * boundaries for the baseline but never modifies the production engine.
 */
public record ThresholdConfiguration(
    String name, int lowMax, int mediumMax, int highMax, boolean baseline) {

  public ThresholdConfiguration {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("threshold configuration name is required");
    }
    if (lowMax < 0 || mediumMax < 0 || highMax < 0 || highMax > RiskLevels.MAX_SCORE) {
      throw new IllegalArgumentException("threshold boundaries must be within 0..100");
    }
    if (!(lowMax < mediumMax && mediumMax < highMax)) {
      throw new IllegalArgumentException(
          "threshold boundaries must satisfy lowMax < mediumMax < highMax");
    }
  }

  public static ThresholdConfiguration production() {
    return new ThresholdConfiguration(
        "production-baseline",
        RiskLevels.LOW_MAX,
        RiskLevels.MEDIUM_MAX,
        RiskLevels.HIGH_MAX,
        true);
  }

  public static List<ThresholdConfiguration> defaults() {
    return List.of(
        production(),
        new ThresholdConfiguration("allow-30", 30, 69, 84, false),
        new ThresholdConfiguration("allow-20", 20, 69, 84, false),
        new ThresholdConfiguration("allow-50", 50, 69, 84, false),
        new ThresholdConfiguration("stop-80", 39, 69, 79, false),
        new ThresholdConfiguration("stop-90", 39, 69, 89, false),
        new ThresholdConfiguration("balanced-50-80", 50, 69, 79, false),
        new ThresholdConfiguration("recall-first-20-70", 20, 60, 69, false),
        new ThresholdConfiguration("friction-first-55-95", 55, 75, 94, false));
  }

  public RiskLevel levelFor(int score) {
    requireScore(score);
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

  public RecommendedAction actionFor(int score) {
    requireScore(score);
    if (score <= lowMax) {
      return RecommendedAction.ALLOW;
    }
    if (score <= highMax) {
      return RecommendedAction.VERIFY;
    }
    return RecommendedAction.STOP;
  }

  private static void requireScore(int score) {
    if (score < 0 || score > RiskLevels.MAX_SCORE) {
      throw new IllegalArgumentException("score must be within 0..100");
    }
  }
}
