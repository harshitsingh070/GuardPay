package com.guardpay.risk.signal;

/**
 * Automatically derivable payment signals, in stable evaluation order.
 * Device/AI signals (call, language, social engineering) are listed for
 * completeness but report unavailable until their integrations exist.
 */
public enum PaymentSignalType {
  UNUSUAL_AMOUNT,
  REPEATED_ATTEMPTS,
  NEW_RECIPIENT,
  RECIPIENT_AMOUNT_ANOMALY,
  UNKNOWN_MERCHANT,
  UNEXPECTED_INVOICE,
  SUSPICIOUS_URL,
  ACTIVE_CALL,
  URGENT_LANGUAGE,
  SOCIAL_ENGINEERING_DETECTED,
  // AI-derived evidence codes (structured LLM output only, never client input).
  IMPERSONATION,
  PRESSURE_TO_PAY,
  SECRECY_REQUEST,
  SUSPICIOUS_PAYMENT_INSTRUCTION,
  SCAM_PATTERN,
  SUSPICIOUS_URL_STRUCTURE,
  UNEXPECTED_INVOICE_CONTEXT,
  // Deterministic policy: high-value payment with no verifiable context.
  MISSING_CONTEXT,
  // Account-takeover context derived from trusted history and network data.
  NEW_DEVICE,
  NEW_GEO,
  IMPOSSIBLE_TRAVEL,
  VPN_PROXY,
  // Multi-window velocity and counterparty reputation.
  VELOCITY_HOURLY,
  VELOCITY_DAILY,
  BLOCKLISTED_RECIPIENT,
  // A known payee whose new request matches a compromised-account scam.
  KNOWN_RECIPIENT_SCAM,
  // Structuring: many sub-threshold payments to one recipient.
  SPLIT_PAYMENT,
  // Adaptive behavioral profiling signals (personalized deviations from
  // learned user baselines, never universal thresholds).
  AMOUNT_DEVIATION,
  UNUSUAL_PAYMENT_HOUR,
  RECIPIENT_BEHAVIOR_DEVIATION,
  TRANSACTION_FREQUENCY_ANOMALY,
  GEOGRAPHIC_BEHAVIOR_DEVIATION,
  DEVICE_BEHAVIOR_DEVIATION,
  OVERALL_BEHAVIOR_DEVIATION,
  // First-time high-value payment detection engine signals
  FIRST_HIGH_VALUE_PAYMENT,
  FIRST_HIGH_VALUE_TO_RECIPIENT,
  FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE,
  SIGNIFICANT_AMOUNT_DEVIATION,
  AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM,
  RAPID_AMOUNT_ESCALATION,
  // Global recipient reputation evidence (display only; scored by the engine's
  // bounded reputation contribution, never by correlation rules).
  GLOBAL_RECIPIENT_REPORTED,
  GLOBAL_RECIPIENT_UNDER_REVIEW,
  GLOBAL_RECIPIENT_CORROBORATED_SUSPICIOUS,
  GLOBAL_RECIPIENT_CONFIRMED_FRAUD
}
