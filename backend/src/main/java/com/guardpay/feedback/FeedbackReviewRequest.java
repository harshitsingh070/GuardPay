package com.guardpay.feedback;

import java.math.BigDecimal;

/** Admin review request; confidence is optional and bounded to 0..1. */
public record FeedbackReviewRequest(
    FeedbackReviewAction action, BigDecimal confidence, String note) {}
