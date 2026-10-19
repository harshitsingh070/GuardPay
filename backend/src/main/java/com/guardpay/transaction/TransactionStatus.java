package com.guardpay.transaction;

/** Lifecycle state of a transaction. Stored as {@code EnumType.STRING}. */
public enum TransactionStatus {
  PENDING,
  COMPLETED,
  FAILED,
  CANCELLED
}
