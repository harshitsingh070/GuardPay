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
  REPEATED_ATTEMPTS(
      "Repeated payment attempts",
      "Several payment attempts were made in a short period of time.",
      15),
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
      20);

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
