package com.guardpay.intelligence.domain;

/** Whether an identifier was independently verified for its global entity. */
public enum IdentifierVerificationStatus {
  UNVERIFIED,
  VERIFIED,
  REVOKED
}
