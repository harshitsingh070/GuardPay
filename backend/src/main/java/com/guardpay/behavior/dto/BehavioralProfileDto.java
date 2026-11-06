package com.guardpay.behavior.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only profile summary for the authenticated user. Never exposes
 * another user's profile or raw statistics that could enable profile
 * poisoning.
 */
public record BehavioralProfileDto(
    UUID userId,
    String profileStatus,
    Integer transactionCount,
    OffsetDateTime profileLastUpdated,
    String dataQualityScore,
    AmountBehavior amountBehavior,
    FrequencyBehavior frequencyBehavior,
    TimeBehavior timeBehavior,
    RecipientBehavior recipientBehavior,
    LocationBehavior locationBehavior,
    DeviceBehavior deviceBehavior,
    List<BehavioralDimensionStatus> behavioralDimensions) {

  public record AmountBehavior(
      String availability,
      BigDecimal medianAmount,
      BigDecimal typicalMin,
      BigDecimal typicalMax,
      BigDecimal p90Amount,
      BigDecimal p95Amount) {}

  public record FrequencyBehavior(
      String availability,
      BigDecimal paymentsPerDay,
      BigDecimal paymentsPerWeek,
      BigDecimal avgIntervalMinutes) {}

  public record TimeBehavior(
      String availability,
      Integer peakHour,
      BigDecimal weekendPaymentRatio,
      BigDecimal nightPaymentRatio,
      List<Integer> typicalHours) {}

  public record RecipientBehavior(
      String availability,
      Integer uniqueRecipientCount,
      Integer establishedRecipientCount,
      BigDecimal newRecipientRatio,
      BigDecimal medianRecipientAmount) {}

  public record LocationBehavior(
      String availability,
      Integer geoCountryCount,
      List<String> typicalCountries) {}

  public record DeviceBehavior(
      String availability,
      Integer deviceCount,
      List<String> familiarDeviceFingerprints) {}

  public record BehavioralDimensionStatus(
      String dimension, String status, String reason) {}
}
