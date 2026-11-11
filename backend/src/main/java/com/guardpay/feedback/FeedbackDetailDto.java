package com.guardpay.feedback;

import java.util.List;

/** Feedback with its append-only history. */
public record FeedbackDetailDto(
    FraudFeedbackDto feedback,
    List<FeedbackEventDto> events,
    List<FeedbackReviewDto> reviews) {}
