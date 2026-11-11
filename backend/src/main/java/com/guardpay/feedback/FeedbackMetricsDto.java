package com.guardpay.feedback;

/**
 * Evaluation metrics derived from explicit labels.
 *
 * <p>A "flagged" decision is VERIFY or STOP. Null precision/recall means the
 * denominator is zero; the value is never fabricated.
 */
public record FeedbackMetricsDto(
    long total,
    long fraudLabels,
    long legitimateLabels,
    long truePositive,
    long falsePositive,
    long trueNegative,
    long falseNegative,
    long unclassified,
    Double precision,
    Double recall) {}
