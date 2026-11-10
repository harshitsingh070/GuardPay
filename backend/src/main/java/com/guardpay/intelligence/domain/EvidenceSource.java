package com.guardpay.intelligence.domain;

/**
 * Origin of fraud evidence with a fixed reliability weight.
 *
 * <p>A user report alone is weak evidence. Only verified investigations and
 * confirmed incidents carry full weight; disputes and refunds are explicitly
 * not proof of fraud.
 */
public enum EvidenceSource {
  USER_REPORT(0.35),
  PROVIDER_DISPUTE_OUTCOME(0.55),
  INDEPENDENT_CORROBORATED_REPORT(0.70),
  MANUAL_REVIEW(0.90),
  CONFIRMED_FRAUD_INCIDENT(1.00),
  VERIFIED_INVESTIGATION(1.00),
  AI_SUSPICION(0.25);

  private final double reliability;

  EvidenceSource(double reliability) {
    this.reliability = reliability;
  }

  public double reliability() {
    return reliability;
  }
}
