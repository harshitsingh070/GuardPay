package com.guardpay.feedback;

/** Admin-only feedback review actions. */
public enum FeedbackReviewAction {
  START_REVIEW,
  VERIFY_FRAUD,
  VERIFY_LEGITIMATE,
  REJECT,
  REOPEN
}
