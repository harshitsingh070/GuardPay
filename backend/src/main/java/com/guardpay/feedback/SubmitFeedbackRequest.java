package com.guardpay.feedback;

/** Request body for an explicit ground-truth label. */
public record SubmitFeedbackRequest(
    FeedbackLabel label,
    FeedbackType feedbackType,
    FeedbackReasonCode reasonCode,
    String relatedFindingCode,
    String idempotencyKey) {

  /** Backward-compatible request used by the original feedback API. */
  public SubmitFeedbackRequest(FeedbackLabel label) {
    this(label, null, null, null, null);
  }
}
