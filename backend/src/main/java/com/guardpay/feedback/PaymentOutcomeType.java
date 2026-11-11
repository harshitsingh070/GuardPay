package com.guardpay.feedback;

/** Financial lifecycle outcome; never a fraud label by itself. */
public enum PaymentOutcomeType {
  CAPTURED,
  REFUNDED,
  REVERSED,
  DISPUTED,
  FAILED,
  CANCELLED
}
