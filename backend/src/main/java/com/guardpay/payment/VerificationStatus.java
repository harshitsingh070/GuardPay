package com.guardpay.payment;

/** Lifecycle of a controlled VERIFY challenge. Stored as {@code EnumType.STRING}. */
public enum VerificationStatus {
  PENDING,
  VERIFIED,
  FAILED,
  EXPIRED
}
