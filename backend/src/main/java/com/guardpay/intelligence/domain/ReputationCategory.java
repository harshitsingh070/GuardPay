package com.guardpay.intelligence.domain;

/** Coarse, user-safe classification of global recipient reputation. */
public enum ReputationCategory {
  NO_DATA,
  LOW_RISK_EVIDENCE,
  ELEVATED_RISK,
  HIGH_RISK,
  CONFIRMED_FRAUD,
  CLEARED
}
