package com.guardpay.payment;

import com.guardpay.transaction.TransactionStatus;

/**
 * Guarded-payment outcome and lifecycle states (API contract).
 *
 * <p>The first three values describe the risk decision at initiation. The
 * remaining values mirror persisted {@link TransactionStatus} values so the API
 * and frontend can track a payment from order creation through capture.
 */
public enum GuardedPaymentStatus {
  BLOCKED,
  VERIFICATION_REQUIRED,
  PAYPAL_AUTHORIZATION_REQUIRED,
  PENDING,
  APPROVED,
  COMPLETED,
  CANCELLED,
  FAILED;

  public static GuardedPaymentStatus from(TransactionStatus status) {
    return switch (status) {
      case PENDING -> PENDING;
      case APPROVED -> APPROVED;
      case COMPLETED -> COMPLETED;
      case FAILED -> FAILED;
      case CANCELLED -> CANCELLED;
    };
  }
}
