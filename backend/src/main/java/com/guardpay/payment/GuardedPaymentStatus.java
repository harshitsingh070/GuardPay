package com.guardpay.payment;

/**
 * Guarded-payment outcome states (API contract).
 *
 * <p>Persistence keeps using {@code TransactionStatus}: PENDING covers
 * authorization-required and verification-required, CANCELLED covers
 * blocked, COMPLETED/FAILED come from capture/webhooks.
 */
public enum GuardedPaymentStatus {
  BLOCKED,
  VERIFICATION_REQUIRED,
  PAYPAL_AUTHORIZATION_REQUIRED
}
