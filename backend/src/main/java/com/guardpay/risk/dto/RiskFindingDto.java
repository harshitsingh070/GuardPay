package com.guardpay.risk.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One evaluated signal, mirroring the engine finding. Persisted on the
 * transaction's assessment (as JSON) so history can show why a decision was
 * made.
 *
 * <p>Behavioral metadata fields (severity, confidence, deviationRatio,
 * observedValue, expectedValue, profileWindow, contributingFactors,
 * evidence) are populated when the signal originates from the adaptive
 * behavioral profiling engine. They are null for deterministic signals.
 */
public record RiskFindingDto(
    String code,
    String title,
    String description,
    int points,
    boolean triggered,
    String severity,
    Double confidence,
    BigDecimal deviationRatio,
    BigDecimal observedValue,
    BigDecimal expectedValue,
    String profileWindow,
    List<String> contributingFactors,
    String evidence) {

  public RiskFindingDto(
      String code, String title, String description, int points, boolean triggered) {
    this(
        code, title, description, points, triggered, null, null, null, null, null, null, null,
        null);
  }
}
