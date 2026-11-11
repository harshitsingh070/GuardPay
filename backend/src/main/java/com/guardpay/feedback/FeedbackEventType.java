package com.guardpay.feedback;

/** Append-only feedback history event types. */
public enum FeedbackEventType {
  SUBMITTED,
  UPDATED,
  REVIEW_STARTED,
  VERIFIED,
  REJECTED,
  REOPENED,
  INVALIDATED
}
