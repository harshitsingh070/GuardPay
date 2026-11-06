package com.guardpay.behavior.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Safe summary of established behavior and recent anomalies for the
 * dashboard. No raw statistics.
 */
public record BehavioralProfileSummaryDto(
    String profileStatus,
    Integer transactionCount,
    OffsetDateTime profileLastUpdated,
    String normalAmountRange,
    String typicalPaymentHours,
    String recipientFamiliarity,
    String deviceFamiliarity,
    List<String> recentAnomalies) {}
