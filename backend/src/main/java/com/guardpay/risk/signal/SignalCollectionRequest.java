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
    String contentUrl,
    UUID deviceId,
    String deviceFingerprint,
    String clientIp,
    String geoCountry,
    String geoCity,
    Integer asn,
    Boolean vpnOrProxy) {

  /** Backwards-compatible constructor for callers without account-takeover context. */
  public SignalCollectionRequest(
      BigDecimal amount,
      String currency,
      UUID userId,
      UUID recipientId,
      UUID merchantId,
      UUID excludeTransactionId,
      String contentText,
      String contentUrl) {
    this(
        amount,
        currency,
        userId,
        recipientId,
        merchantId,
        excludeTransactionId,
        contentText,
        contentUrl,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

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
