package com.guardpay.risk.correlation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Declarative description of one suspicious signal combination.
 *
 * <p>A rule fires only when every requirement is satisfied by a present,
 * sufficiently confident signal. {@code weight} scales the interaction;
 * {@code minimumInteraction} is the noise floor below which the combination
 * is considered coincidental. Rules are versioned so persisted findings remain
 * explainable after tuning.
 */
public record CorrelationRule(
    String code,
    CorrelationCategory category,
    String title,
    String description,
    List<SignalRequirement> requirements,
    double weight,
    double minimumInteraction,
    double minimumConfidence,
    int maximumContribution,
    String ruleVersion,
    boolean enabled) {

  public CorrelationRule {
    requirements = List.copyOf(requirements);
  }

  public Set<EvidenceFamily> families() {
    Set<EvidenceFamily> families = new LinkedHashSet<>();
    for (SignalRequirement requirement : requirements) {
      families.add(requirement.family());
    }
    return families;
  }

  public Set<String> signalCodes() {
    Set<String> codes = new LinkedHashSet<>();
    for (SignalRequirement requirement : requirements) {
      codes.addAll(requirement.codes());
    }
    return codes;
  }
}
