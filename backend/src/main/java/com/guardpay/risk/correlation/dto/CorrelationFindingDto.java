package com.guardpay.risk.correlation.dto;

import com.guardpay.risk.correlation.CorrelationFinding;
import java.util.List;

/**
 * API/audit representation of a fired correlation rule.
 *
 * <p>Mirrors the domain finding one-to-one. {@code contributionPoints} is the
 * already de-duplicated number of points added to the engine score.
 */
public record CorrelationFindingDto(
    String correlationCode,
    String category,
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

  public CorrelationFindingDto {
    participatingSignals = participatingSignals == null ? List.of() : List.copyOf(participatingSignals);
    evidenceFamilies = evidenceFamilies == null ? List.of() : List.copyOf(evidenceFamilies);
    triggeredConditions = triggeredConditions == null ? List.of() : List.copyOf(triggeredConditions);
    unavailableConditions =
        unavailableConditions == null ? List.of() : List.copyOf(unavailableConditions);
  }

  public static CorrelationFindingDto from(CorrelationFinding finding) {
    return new CorrelationFindingDto(
        finding.correlationCode(),
        finding.category().name(),
        finding.title(),
        finding.description(),
        finding.severity(),
        finding.confidence(),
        finding.interactionStrength(),
        finding.contributionPoints(),
        finding.participatingSignals(),
        finding.evidenceFamilies(),
        finding.triggeredConditions(),
        finding.unavailableConditions(),
        finding.ruleVersion(),
        finding.overlapDiscounted(),
        finding.explanation());
  }
}
