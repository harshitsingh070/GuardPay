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
  MISSING_CONTEXT
}
