package com.guardpay.behavior.dto;

import java.math.BigDecimal;

/**
 * Currency-specific high-value spending baseline metrics for the authenticated user.
 */
public record HighValueBaselineDto(
    String currency,
    String profileStatus,
    int transactionCount,
    BigDecimal medianAmount,
    BigDecimal madAmount,
    BigDecimal historicalMax,
    BigDecimal p75Amount,
    BigDecimal p90Amount,
    BigDecimal p95Amount,
    BigDecimal typicalHighValueThreshold,
    boolean established) {}

