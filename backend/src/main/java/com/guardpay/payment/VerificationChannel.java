package com.guardpay.payment;

import java.util.UUID;

/**
 * Out-of-band delivery for a verification challenge. The API response and the
 * browser must never contain the code; only this channel sees it.
 */
public interface VerificationChannel {

  void deliver(UUID paymentId, String code);

  /** True when a usable code is still held for this payment. */
  default boolean has(UUID paymentId) {
    return false;
  }

  /** Drops any retained copy once the challenge is no longer usable. */
  default void clear(UUID paymentId) {}
}
