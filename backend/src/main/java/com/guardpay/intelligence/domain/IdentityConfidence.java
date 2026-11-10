package com.guardpay.intelligence.domain;

/** Confidence that two identifier observations belong to the same recipient. */
public enum IdentityConfidence {
  LOW(0.35),
  MEDIUM(0.60),
  HIGH(0.85),
  VERY_HIGH(1.00);

  private final double factor;

  IdentityConfidence(double factor) {
    this.factor = factor;
  }

  /** Bounded identity factor used when translating reputation into risk points. */
  public double factor() {
    return factor;
  }
}
