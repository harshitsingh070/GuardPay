package com.guardpay.risk.correlation;

import com.guardpay.risk.signal.SignalSource;

/**
 * A {@link com.guardpay.risk.signal.PaymentSignal} normalized for correlation.
 *
 * <p>{@code confidence} is an observation confidence (not a risk score).
 * Deterministic signals are trusted facts and receive a high fixed confidence;
 * AI and behavioral signals carry their own calibrated confidence.
 */
public record CorrelationSignal(
    String code,
    EvidenceFamily family,
    SignalSource source,
    SignalAvailability availability,
    String severity,
    double confidence,
    int originalPoints,
    String evidence,
    boolean derived) {

  public boolean present() {
    return availability == SignalAvailability.PRESENT;
  }

  public boolean usable() {
    return present() && confidence > 0.0;
  }
}
