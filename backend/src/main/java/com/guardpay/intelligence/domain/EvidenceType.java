package com.guardpay.intelligence.domain;

/** Machine-readable fraud evidence categories. */
public enum EvidenceType {
  RECIPIENT_REPORTED,
  FRAUD_CONFIRMED,
  DISPUTE_OPENED,
  REFUND_ISSUED,
  REVERSAL_ISSUED,
  MANUAL_CORROBORATION,
  INVESTIGATION_CONFIRMED,
  ACCOUNT_COMPROMISE,
  AI_SUSPICION
}
