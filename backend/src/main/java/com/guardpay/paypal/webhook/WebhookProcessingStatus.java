package com.guardpay.paypal.webhook;

/** Lifecycle of a received PayPal webhook event. Stored, never recomputed. */
public enum WebhookProcessingStatus {
  RECEIVED,
  PROCESSED,
  IGNORED,
  FAILED
}
