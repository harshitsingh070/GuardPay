package com.guardpay.intelligence.domain;

/** Verification state of a single evidence observation. */
public enum EvidenceVerificationStatus {
  UNVERIFIED,
  VERIFIED,
  REJECTED,
  REVOKED
}
