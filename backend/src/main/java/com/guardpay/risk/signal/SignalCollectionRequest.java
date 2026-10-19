package com.guardpay.risk.signal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Trusted input for signal collection. Deliberately contains NO risk
 * booleans — amounts and identities only. Anything not derivable from these
 * plus the database reports unavailable rather than guessed.
 */
public record SignalCollectionRequest(
    BigDecimal amount,
    String currency,
    UUID userId,
    UUID recipientId,
    UUID merchantId,
    UUID excludeTransactionId) {}
