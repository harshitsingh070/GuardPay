package com.guardpay.feedback;

/** Lifecycle of an explicit feedback label. */
public enum FeedbackStatus {
  SUBMITTED,
  UNDER_REVIEW,
  VERIFIED_FRAUD,
  VERIFIED_LEGITIMATE,
  REJECTED,
  SUPERSEDED;

  public boolean isVerified() {
    return this == VERIFIED_FRAUD || this == VERIFIED_LEGITIMATE;
  }

  public boolean isOpen() {
    return this == SUBMITTED || this == UNDER_REVIEW;
  }
}
