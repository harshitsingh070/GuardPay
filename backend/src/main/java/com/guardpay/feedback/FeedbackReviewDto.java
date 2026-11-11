package com.guardpay.feedback;

import java.time.OffsetDateTime;

/** Admin review record for a feedback label. */
public record FeedbackReviewDto(
    String action,
    String fromStatus,
    String toStatus,
    Double confidence,
    String note,
    OffsetDateTime reviewedAt) {}
