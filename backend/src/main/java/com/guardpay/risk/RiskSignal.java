package com.guardpay.risk;

/**
 * The GuardPay risk signals in fixed evaluation order.
 *
 * <p>Single source of truth for signal code, user-facing copy, and weight.
 * Finding order in {@code RiskEvaluationResult} follows this declaration
 * order, which keeps engine output deterministic.
 */
public enum RiskSignal {
  ACTIVE_CALL(
      "Active phone call",
      "User is currently on an active phone call.",
      15),
  NEW_RECIPIENT(
      "New recipient",
      "This payment is going to a new recipient.",
      20),
  LARGE_AMOUNT(
      "Unusually large amount",
      "Payment amount is significantly higher than the user's normal transaction amount.",
      20),
  RECIPIENT_AMOUNT_ANOMALY(
      "Unusual amount for this recipient",
      "Payment amount is significantly higher than this recipient's normal completed payment amount.",
      20),
  REPEATED_ATTEMPTS(
      "Repeated payment attempts",
      "Several payment attempts were made in a short period of time.",
      15),
  VELOCITY_HOURLY(
      "Sustained payment velocity",
      "Five or more payment attempts were made in the last hour.",
      15),
  VELOCITY_DAILY(
      "High payment velocity",
      "Ten or more payment attempts were made in the last 24 hours.",
      20),
  BLOCKLISTED_RECIPIENT(
      "Reported recipient",
      "This recipient is on the user's fraud blocklist.",
      25),
  UNKNOWN_MERCHANT(
      "Unknown merchant",
      "Merchant is not recognized from the user's transaction history.",
      10),
  UNEXPECTED_INVOICE(
      "Unexpected invoice",
      "Payment is associated with an unexpected invoice or payment request.",
      15),
  URGENT_LANGUAGE(
      "Urgent language",
      "Urgent or pressuring language was detected.",
      10),
  SUSPICIOUS_URL(
      "Suspicious URL",
      "Payment context contains a suspicious URL.",
      10),
  SOCIAL_ENGINEERING(
      "Known scam pattern",
      "Behavior is consistent with a known social-engineering payment scam pattern.",
      20),
  KNOWN_RECIPIENT_SCAM(
      "Known recipient scam pattern",
      "A previously known recipient is asking for a payment in a way that matches a compromised-account scam.",
      50),
  SPLIT_PAYMENT(
      "Structured or split payments",
      "Several small payments to the same recipient add up to a high-value payment in a short period.",
      40),
  MISSING_CONTEXT(
      "Unverified high-value payment",
      "A high-value payment was requested without message, link, or trusted device context.",
      20),
  NEW_DEVICE(
      "New device",
      "This payment was started from a device that has not been used for this account before.",
      20),
  NEW_GEO(
      "Unfamiliar location",
      "This payment was started from a country that has not been seen for this account before.",
      15),
  IMPOSSIBLE_TRAVEL(
      "Impossible travel",
      "This payment appears to come from a different country only a short time after the previous payment.",
      25),
  VPN_PROXY(
      "VPN or proxy",
      "The payment connection appears to use a VPN, proxy, or hosting provider.",
      10),
  AMOUNT_DEVIATION(
      "Unusual amount for your pattern",
      "Payment amount deviates significantly from your learned behavioral baseline.",
      15),
  UNUSUAL_PAYMENT_HOUR(
      "Payment outside usual hours",
      "Payment is occurring at a time outside your typical activity window.",
      10),
  RECIPIENT_BEHAVIOR_DEVIATION(
      "Unusual recipient pattern",
      "Payment to a recipient that does not match your established payment relationships.",
      15),
  TRANSACTION_FREQUENCY_ANOMALY(
      "Unusual payment frequency",
      "Payment frequency deviates from your learned behavioral pattern.",
      10),
  GEOGRAPHIC_BEHAVIOR_DEVIATION(
      "Unusual payment location",
      "Payment is from a location outside your established geographic pattern.",
      15),
  DEVICE_BEHAVIOR_DEVIATION(
      "Unrecognized device",
      "Payment is from a device not previously used for your account.",
      15),
  OVERALL_BEHAVIOR_DEVIATION(
      "Multiple behavioral anomalies",
      "Several behavioral dimensions deviate from your established pattern simultaneously.",
      20),
  FIRST_HIGH_VALUE_PAYMENT(
      "First high-value payment",
      "Payment is substantially higher than your historical baseline with no prior high-value history.",
      20),
  FIRST_HIGH_VALUE_TO_RECIPIENT(
      "First high-value payment to recipient",
      "Payment amount is significantly higher than any previous payment sent to this recipient.",
      20),
  FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE(
      "First-time payment to new recipient is high-value",
      "Payment to a new recipient is unusually large relative to your normal spending baseline.",
      25),
  SIGNIFICANT_AMOUNT_DEVIATION(
      "Significant amount deviation",
      "Payment amount substantially exceeds your established spending distribution.",
      20),
  AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM(
      "Exceeds historical maximum",
      "Payment amount exceeds the largest payment previously observed for your account.",
      15),
  RAPID_AMOUNT_ESCALATION(
      "Rapid amount escalation",
      "Payment represents an unusually rapid increase in spending amount over recent transactions.",
      15);

  private final String title;
  private final String description;
  private final int points;

  RiskSignal(String title, String description, int points) {
    this.title = title;
    this.description = description;
    this.points = points;
  }

  /** Stable signal identifier (the enum name, e.g. {@code ACTIVE_CALL}). */
  public String code() {
    return name();
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public int points() {
    return points;
  }
}
