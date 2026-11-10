package com.guardpay.intelligence.domain;

/** How strongly the payment's recipient identifier matches a global entity. */
public enum IdentifierMatchLevel {
  EXACT_VERIFIED_MATCH(1.00),
  EXACT_UNVERIFIED_MATCH(0.50),
  POSSIBLE_MATCH(0.25),
  NO_MATCH(0.00);

  private final double multiplier;

  IdentifierMatchLevel(double multiplier) {
    this.multiplier = multiplier;
  }

  public double multiplier() {
    return multiplier;
  }
}
