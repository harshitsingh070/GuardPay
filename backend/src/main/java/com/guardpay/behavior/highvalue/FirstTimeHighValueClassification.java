package com.guardpay.behavior.highvalue;

/**
 * High-level classification categories for first-time high-value payment analysis.
 *
 * <p>Distinguishes user-specific deviation, recipient novelty, escalation,
 * and legitimate high-value spending patterns.
 */
public enum FirstTimeHighValueClassification {
  /**
   * User has no established high-value history, and the current payment
   * is significantly larger than their historical baseline.
   */
  FIRST_HIGH_VALUE_PAYMENT,

  /**
   * User has paid this recipient before, but never sent a payment of
   * comparable magnitude to that recipient.
   */
  FIRST_HIGH_VALUE_TO_RECIPIENT,

  /**
   * Recipient is new to the user, and the payment is unusually large relative
   * to the user's spending history.
   */
  FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE,

  /**
   * Amount is substantially above the user's established spending distribution,
   * regardless of recipient novelty.
   */
  SIGNIFICANT_AMOUNT_DEVIATION,

  /**
   * Amount exceeds the highest eligible completed payment previously recorded
   * for this user in this currency.
   */
  AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM,

  /**
   * Recent payment amounts show a sudden steep increase relative to
   * historical spending patterns.
   */
  RAPID_AMOUNT_ESCALATION,

  /**
   * Payment is large in absolute terms, but is fully consistent with the
   * user's established high-value payment history.
   */
  LEGITIMATE_HIGH_VALUE_ESTABLISHED,

  /**
   * Payment amount and recipient pattern are within normal historical boundaries.
   */
  NORMAL_PAYMENT,

  /**
   * Insufficient historical data exists for this user in this currency.
   * Cold-start condition; missing history is not treated as fraud.
   */
  COLD_START_INSUFFICIENT_DATA
}

