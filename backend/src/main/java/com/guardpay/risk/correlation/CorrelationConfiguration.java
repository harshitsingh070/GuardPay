package com.guardpay.risk.correlation;

/**
 * Tunable bounds for the correlation layer.
 *
 * <p>{@code freshnessFactor} is currently fixed at 1.0 because every signal
 * is collected for the live request. It exists so time-decayed evidence can be
 * added later without changing the scoring formula.
 */
public record CorrelationConfiguration(
    int maxContributionPoints,
    double interactionThreshold,
    double minimumSignalConfidence,
    double overlapThreshold,
    double dependencyDiscount,
    int familyContributionCap,
    double freshnessFactor,
    double strengthFloor) {

  public static CorrelationConfiguration defaults() {
    return new CorrelationConfiguration(25, 0.12, 0.5, 0.6, 0.5, 12, 1.0, 0.05);
  }

  public CorrelationConfiguration {
    if (maxContributionPoints < 0) {
      throw new IllegalArgumentException("maxContributionPoints must not be negative");
    }
    if (familyContributionCap < 0) {
      throw new IllegalArgumentException("familyContributionCap must not be negative");
    }
    if (freshnessFactor < 0.0 || freshnessFactor > 1.0) {
      throw new IllegalArgumentException("freshnessFactor must be between 0 and 1");
    }
  }
}
