package com.guardpay.transaction;

/** Kind of money movement a transaction represents. Stored as {@code EnumType.STRING}. */
public enum TransactionType {
  PAYMENT,
  REFUND,
  TRANSFER
}
