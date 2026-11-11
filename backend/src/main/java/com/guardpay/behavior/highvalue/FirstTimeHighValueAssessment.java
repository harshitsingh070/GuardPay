package com.guardpay.behavior.highvalue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Rich quantitative assessment produced by {@link FirstTimeHighValueDetectionEngine}.
 *
 * <p>Preserves full lineage, statistical metrics, recipient novelty, and
 * human-readable explanations. Never fabricates baselines for cold-start users.
 */
public record FirstTimeHighValueAssessment(
    FirstTimeHighValueClassification classification,
    boolean detected,
    String severity,
    Double confidence,
    BigDecimal observedAmount,
    BigDecimal expectedAmount,
    String currency,
    BigDecimal historicalMedian,
    BigDecimal historicalMad,
    BigDecimal modifiedZScore,
    BigDecimal amountRatio,
    Double percentileRank,
    BigDecimal historicalMax,
    BigDecimal ratioOverHistoricalMax,
    boolean isNewRecipient,
    int recipientPaymentCount,
    BigDecimal recipientMedian,
    BigDecimal recipientMax,
    BigDecimal ratioOverRecipientMedian,
    BigDecimal ratioOverRecipientMax,
    String baselineWindow,
    String profileStatus,
    List<String> contributingFactors,
    String evidence,
    String title,
    OffsetDateTime evaluatedAt) {

  public static FirstTimeHighValueAssessment coldStart(
      BigDecimal amount,
      String currency,
      String reason,
      String profileStatus,
      double confidence,
      OffsetDateTime evaluatedAt) {
    return new FirstTimeHighValueAssessment(
        FirstTimeHighValueClassification.COLD_START_INSUFFICIENT_DATA,
        false,
        "INFO",
        confidence,
        amount,
        null,
        currency,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true,
        0,
        null,
        null,
        null,
        null,
        "NONE",
        profileStatus != null ? profileStatus : "INSUFFICIENT_DATA",
        List.of("Insufficient payment history in " + (currency != null ? currency : "currency")),
        "Not enough completed payment history (" + reason + ") to establish a reliable baseline.",
        "Cold-start: Insufficient history",
        evaluatedAt);
  }

  public static FirstTimeHighValueAssessment normal(
      BigDecimal amount,
      String currency,
      BigDecimal median,
      BigDecimal mad,
      BigDecimal historicalMax,
      boolean isNewRecipient,
      int recipientPaymentCount,
      String window,
      String profileStatus,
      OffsetDateTime evaluatedAt) {
    return new FirstTimeHighValueAssessment(
        FirstTimeHighValueClassification.NORMAL_PAYMENT,
        false,
        "LOW",
        0.90,
        amount,
        median,
        currency,
        median,
        mad,
        null,
        median != null && median.signum() > 0 ? amount.divide(median, 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ONE,
        null,
        historicalMax,
        historicalMax != null && historicalMax.signum() > 0 ? amount.divide(historicalMax, 4, java.math.RoundingMode.HALF_UP) : null,
        isNewRecipient,
        recipientPaymentCount,
        null,
        null,
        null,
        null,
        window,
        profileStatus,
        List.of("Amount is within normal historical distribution"),
        "Payment amount is consistent with normal historical spending behavior.",
        "Normal payment amount",
        evaluatedAt);
  }

  public static FirstTimeHighValueAssessment legitimateEstablished(
      BigDecimal amount,
      String currency,
      BigDecimal median,
      BigDecimal historicalMax,
      boolean isNewRecipient,
      int recipientPaymentCount,
      String window,
      String profileStatus,
      OffsetDateTime evaluatedAt) {
    return new FirstTimeHighValueAssessment(
        FirstTimeHighValueClassification.LEGITIMATE_HIGH_VALUE_ESTABLISHED,
        false,
        "LOW",
        0.95,
        amount,
        median,
        currency,
        median,
        null,
        null,
        median != null && median.signum() > 0 ? amount.divide(median, 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ONE,
        null,
        historicalMax,
        historicalMax != null && historicalMax.signum() > 0 ? amount.divide(historicalMax, 4, java.math.RoundingMode.HALF_UP) : null,
        isNewRecipient,
        recipientPaymentCount,
        null,
        null,
        null,
        null,
        window,
        profileStatus,
        List.of("User has an established history of high-value payments"),
        "High amount is supported by established account spending history.",
        "Legitimate high-value payment (established history)",
        evaluatedAt);
  }
}

