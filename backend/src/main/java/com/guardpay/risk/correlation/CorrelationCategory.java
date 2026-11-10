package com.guardpay.risk.correlation;

/** High-level family of a correlation rule, used for grouping and display. */
public enum CorrelationCategory {
  ACCOUNT_TAKEOVER,
  SOCIAL_ENGINEERING,
  VELOCITY_STRUCTURING,
  COUNTERPARTY_RISK,
  NETWORK_ANOMALY,
  BEHAVIORAL_SHIFT,
  COMBINED_DEVIATION
}
