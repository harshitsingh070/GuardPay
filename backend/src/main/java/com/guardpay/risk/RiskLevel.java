package com.guardpay.risk;

/**
 * Severity bucket for a GuardPay risk score. Stored as {@code EnumType.STRING}.
 */
public enum RiskLevel {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
