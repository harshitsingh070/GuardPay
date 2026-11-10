package com.guardpay.intelligence.dto;

/** Aggregate counts for the requesting user's own reports. */
public record IntelligenceAggregateDto(
    long totalReports,
    long submitted,
    long underReview,
    long accepted,
    long rejected,
    long distinctRecipients) {}
