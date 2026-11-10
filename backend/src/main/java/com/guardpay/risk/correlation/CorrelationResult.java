package com.guardpay.risk.correlation;

import java.util.List;

/**
 * Result of one correlation pass.
 *
 * <p>{@code status} is one of {@code AVAILABLE}, {@code NO_SIGNALS},
 * {@code NO_MATCH}, or {@code UNAVAILABLE}. Only {@code AVAILABLE} with a
 * non-zero contribution changes the engine score; every other status is
 * evidence-neutral by construction.
 */
public record CorrelationResult(
    List<CorrelationFinding> findings,
    int contributionPoints,
    double aggregateStrength,
    String status,
    String explanation) {

  public static final String AVAILABLE = "AVAILABLE";
  public static final String NO_SIGNALS = "NO_SIGNALS";
  public static final String NO_MATCH = "NO_MATCH";
  public static final String UNAVAILABLE = "UNAVAILABLE";

  public CorrelationResult {
    findings = findings == null ? List.of() : List.copyOf(findings);
  }

  public static CorrelationResult empty(String status) {
    return new CorrelationResult(List.of(), 0, 0.0, status, "No correlated signal combination was detected.");
  }

  public static CorrelationResult unavailable() {
    return new CorrelationResult(
        List.of(),
        0,
        0.0,
        UNAVAILABLE,
        "Signal correlation was unavailable; deterministic signals decided alone.");
  }
}
