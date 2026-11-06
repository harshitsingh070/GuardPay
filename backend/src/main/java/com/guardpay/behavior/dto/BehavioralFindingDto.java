package com.guardpay.behavior.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One behavioral anomaly finding produced by {@link
 * com.guardpay.behavior.AdaptiveBehavioralAnalyzer}. These are evidence
 * only — never a payment decision — and are folded into existing engine
 * signals where they overlap.
 */
public record BehavioralFindingDto(
    String code,
    String title,
    String description,
    String severity,
    Double confidence,
    BigDecimal observedValue,
    BigDecimal expectedValue,
    BigDecimal deviationRatio,
    String profileWindow,
    String profileStatus,
    String evidence,
    List<String> contributingFactors,
    OffsetDateTime evaluatedAt) {}
