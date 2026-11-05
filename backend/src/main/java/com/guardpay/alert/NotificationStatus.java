package com.guardpay.alert;

/** Transactional outbox delivery state. */
public enum NotificationStatus {
  PENDING,
  SENT,
  FAILED,
  SKIPPED
}
