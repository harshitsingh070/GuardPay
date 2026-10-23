package com.guardpay.risk.signal;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Trusted input for signal collection. Deliberately contains NO risk
 * booleans — amounts, identities, and raw message/link context only. Anything
 * not derivable from these plus the database reports unavailable rather than
 * guessed.
 */
public record SignalCollectionRequest(
    BigDecimal amount,
    String currency,
    UUID userId,
    UUID recipientId,
    UUID merchantId,
    UUID excludeTransactionId,
    String contentText,
    String contentUrl) {

  /** Backwards-compatible constructor for callers without content. */
  public SignalCollectionRequest(
      BigDecimal amount,
      String currency,
      UUID userId,
      UUID recipientId,
      UUID merchantId,
      UUID excludeTransactionId) {
    this(amount, currency, userId, recipientId, merchantId, excludeTransactionId, null, null);
  }
}
