package com.guardpay.evaluation;

/** Selects whether a benchmark run may perform threshold exploration. */
public enum BenchmarkDatasetRole {
  DEV(true),
  HELD_OUT(false);

  private final boolean thresholdOptimizationAllowed;

  BenchmarkDatasetRole(boolean thresholdOptimizationAllowed) {
    this.thresholdOptimizationAllowed = thresholdOptimizationAllowed;
  }

  public boolean thresholdOptimizationAllowed() {
    return thresholdOptimizationAllowed;
  }
}
