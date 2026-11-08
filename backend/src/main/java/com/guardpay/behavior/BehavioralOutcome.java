package com.guardpay.behavior;

/**
 * Terminal outcome recorded for a payment attempt.
 *
 * <p>Only {@link #COMPLETED} can contribute to a user's learned behavioral
 * baseline, and only when the payment was not stopped and no later exclusion
 * event exists. Refund/reversal/dispute events are persisted so that a
 * previously learned payment can be removed from the baseline.
 */
public enum BehavioralOutcome {
  COMPLETED,
  REFUNDED,
  REVERSED,
  DISPUTED,
  FAILED,
  CANCELLED
}
