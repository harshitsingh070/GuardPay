package com.guardpay.learning.domain;

/** Champion/challenger lifecycle for a rule-calibration model. */
public enum ModelStatus {
  CANDIDATE,
  APPROVED,
  ACTIVE,
  REJECTED,
  RETIRED,
  ROLLED_BACK,
  INVALIDATED;

  public boolean isChampion() {
    return this == ACTIVE;
  }

  public boolean canApprove() {
    return this == CANDIDATE;
  }

  public boolean canDeploy() {
    return this == APPROVED;
  }
}
