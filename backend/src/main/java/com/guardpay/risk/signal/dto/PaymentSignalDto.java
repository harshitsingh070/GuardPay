package com.guardpay.risk.signal.dto;

import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;

/** One automatically collected signal, as derived by the backend. */
public record PaymentSignalDto(
    PaymentSignalType type,
    boolean detected,
    SignalSource source,
    String reason,
    String evidence,
    String severity,
    Double confidence) {

  public PaymentSignalDto(
      PaymentSignalType type, boolean detected, SignalSource source, String reason, String evidence) {
    this(type, detected, source, reason, evidence, null, null);
  }
}
