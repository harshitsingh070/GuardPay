package com.guardpay.dashboard.dto;

import java.math.BigDecimal;

/** Live decision, latency, alert, and verification metrics for the dashboard. */
public record DashboardSummaryDto(
    long decisions,
    long allowCount,
    long verifyCount,
    long stopCount,
    BigDecimal blockedAmount,
    double averageScore,
    LatencyDto latency,
    AiDto ai,
    AlertStatsDto alerts,
    VerificationStatsDto verification) {

  public record LatencyDto(long p50Ms, long p95Ms, long samples) {}

  public record AiDto(
      long requests, long available, long unavailable, double availabilityPct) {}

  public record AlertStatsDto(
      long total, long critical, long pending, long sent, Long p95DeliveryMs) {}

  public record VerificationStatsDto(
      long pending, long verified, long failed, long expired, double successRatePct) {}
}
