package com.guardpay.behavior.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response from behavioral analysis: profile state plus anomaly findings.
 */
public record BehavioralAnalysisResponse(
    UUID userId,
    String profileStatus,
    Integer transactionCount,
    OffsetDateTime profileLastUpdated,
    String dataQualityScore,
    List<BehavioralFindingDto> findings,
    String overallBehavioralDeviation,
    Boolean available) {}
