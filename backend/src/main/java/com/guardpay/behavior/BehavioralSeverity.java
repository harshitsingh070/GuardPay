package com.guardpay.behavior;

/**
 * Severity levels for behavioral findings. These are not risk scores;
 * they indicate how surprising the observed behavior is relative to the
 * user's learned baseline.
 */
public enum BehavioralSeverity {
  INFO,
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
