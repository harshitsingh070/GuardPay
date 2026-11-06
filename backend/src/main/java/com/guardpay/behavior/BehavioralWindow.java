package com.guardpay.behavior;

/**
 * Rolling historical windows used for behavioral profile computation and
 * anomaly comparison.
 */
public enum BehavioralWindow {
  SHORT_TERM("7_DAYS", 7),
  MEDIUM_TERM("30_DAYS", 30),
  LONG_TERM("90_DAYS", 90);

  private final String label;
  private final int days;

  BehavioralWindow(String label, int days) {
    this.label = label;
    this.days = days;
  }

  public String getLabel() {
    return label;
  }

  public int getDays() {
    return days;
  }
}
