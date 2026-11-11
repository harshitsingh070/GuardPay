package com.guardpay.feedback;

/** Counts by review status for the user/admin dashboards. */
public record FeedbackSummaryDto(
    long total,
    long submitted,
    long underReview,
    long verifiedFraud,
    long verifiedLegitimate,
    long rejected,
    long superseded) {}
