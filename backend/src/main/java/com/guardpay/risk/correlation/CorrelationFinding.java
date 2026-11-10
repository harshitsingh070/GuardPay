package com.guardpay.risk.correlation;

import java.util.List;

/**
 * Explainable output of one fired correlation rule.
 *
 * <p>{@code contributionPoints} is the bounded number of points this rule
 * added to the engine score. It is already de-duplicated against overlapping
 * rules, so the sum over findings equals the total correlation contribution.
 */
public record CorrelationFinding(
    String correlationCode,
    CorrelationCategory category,
    String title,
    String description,
    String severity,
    double confidence,
    double interactionStrength,
    int contributionPoints,
    List<String> participatingSignals,
    List<String> evidenceFamilies,
    List<String> triggeredConditions,
    List<String> unavailableConditions,
    String ruleVersion,
    boolean overlapDiscounted,
    String explanation) {

  public CorrelationFinding {
    participatingSignals = List.copyOf(participatingSignals);
    evidenceFamilies = List.copyOf(evidenceFamilies);
    triggeredConditions = List.copyOf(triggeredConditions);
    unavailableConditions = List.copyOf(unavailableConditions);
  }
}
