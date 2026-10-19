package com.guardpay.risk.signal;

/**
 * One collected signal. {@code reason} is a machine-readable code explaining
 * <em>why not</em> (e.g. {@code INSUFFICIENT_HISTORY}) and is null for plain
 * negative results with sufficient data; {@code evidence} is the human text.
 * Values are backend-derived — never client-supplied.
 *
 * <p>{@code severity}/{@code confidence} are populated only for AI-derived
 * signals (observation metadata, never a risk score); deterministic signals
 * leave them null via the short constructor.
 */
public record PaymentSignal(
    PaymentSignalType type,
    boolean detected,
    SignalSource source,
    String reason,
    String evidence,
    String severity,
    Double confidence) {

  public PaymentSignal(
      PaymentSignalType type, boolean detected, SignalSource source, String reason, String evidence) {
    this(type, detected, source, reason, evidence, null, null);
  }
}
