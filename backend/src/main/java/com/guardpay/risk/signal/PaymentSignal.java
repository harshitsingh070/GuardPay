package com.guardpay.risk.signal;

import java.math.BigDecimal;
import java.util.List;

/**
 * One collected signal. {@code reason} is a machine-readable code explaining
 * <em>why not</em> (e.g. {@code INSUFFICIENT_HISTORY}) and is null for plain
 * negative results with sufficient data; {@code evidence} is the human text.
 * Values are backend-derived — never client-supplied.
 *
 * <p>{@code severity}/{@code confidence} are populated only for AI-derived
 * signals (observation metadata, never a risk score); deterministic signals
 * leave them null via the short constructor.
 *
 * <p>The trailing metadata fields carry the adaptive behavioral profiler's
 * quantitative evidence (deviation ratio, observed vs expected value, profile
 * window, contributing factors) and are null for non-behavioral signals.
 */
public record PaymentSignal(
    PaymentSignalType type,
    boolean detected,
    SignalSource source,
    String reason,
    String evidence,
    String severity,
    Double confidence,
    BigDecimal deviationRatio,
    BigDecimal observedValue,
    BigDecimal expectedValue,
    String profileWindow,
    List<String> contributingFactors) {

  public PaymentSignal(
      PaymentSignalType type, boolean detected, SignalSource source, String reason, String evidence) {
    this(type, detected, source, reason, evidence, null, null, null, null, null, null, null);
  }

  public PaymentSignal(
      PaymentSignalType type,
      boolean detected,
      SignalSource source,
      String reason,
      String evidence,
      String severity,
      Double confidence) {
    this(
        type,
        detected,
        source,
        reason,
        evidence,
        severity,
        confidence,
        null,
        null,
        null,
        null,
        null);
  }
}
