package com.guardpay.behavior.highvalue;

import com.guardpay.behavior.BehavioralWindow;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import com.guardpay.transaction.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * First-Time High-Value Payment Detection Engine.
 *
 * <p>Identifies potentially fraudulent high-value payments using user-specific
 * financial behavior rather than fixed global amount thresholds.
 *
 * <p>Distinguishes:
 * <ul>
 *   <li>High amount in absolute terms vs high amount relative to baseline</li>
 *   <li>First payment to a recipient</li>
 *   <li>First high-value payment to an existing recipient</li>
 *   <li>First high-value payment ever observed for the user</li>
 *   <li>Rapid escalation in payment amount over recent history</li>
 *   <li>Legitimate high-value payments fully supported by established history</li>
 * </ul>
 *
 * <p>Follows all non-negotiable architectural rules:
 * <ul>
 *   <li>Evaluation is strictly read-only; never updates profile baselines</li>
 *   <li>Excludes current in-flight transaction from baseline</li>
 *   <li>Separates currencies strictly (USD, INR, EUR never mixed)</li>
 *   <li>Zero-MAD distributions fall back to percentiles and ratio tests</li>
 *   <li>Cold-start users receive explicit INSUFFICIENT_DATA, never treated as fraud</li>
 * </ul>
 */
@Service
public class FirstTimeHighValueDetectionEngine {

  private static final Logger log = LoggerFactory.getLogger(FirstTimeHighValueDetectionEngine.class);
  private static final BigDecimal NORMAL_DIST_SCALE = new BigDecimal("0.6745");

  private final FirstTimeHighValueConfig config;

  public FirstTimeHighValueDetectionEngine() {
    this(FirstTimeHighValueConfig.defaults());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public FirstTimeHighValueDetectionEngine(FirstTimeHighValueConfig config) {
    this.config = config != null ? config : FirstTimeHighValueConfig.defaults();
  }

  public FirstTimeHighValueConfig getConfig() {
    return config;
  }

  /**
   * Evaluates an incoming payment for first-time high-value anomalies.
   *
   * @param userId authenticated user ID
   * @param currentAmount payment amount
   * @param currency transaction currency (e.g. "USD", "INR")
   * @param recipientId recipient ID (optional)
   * @param eligibleHistory completed historical transactions
   * @param excludeTransactionId in-flight transaction ID to exclude from baseline
   * @param asOf evaluation timestamp
   * @param profileStatus user profile confidence status
   * @return comprehensive assessment
   */
  public FirstTimeHighValueAssessment evaluate(
      UUID userId,
      BigDecimal currentAmount,
      String currency,
      UUID recipientId,
      List<Transaction> eligibleHistory,
      UUID excludeTransactionId,
      OffsetDateTime asOf,
      String profileStatus) {

    if (currentAmount == null || currentAmount.signum() <= 0) {
      return FirstTimeHighValueAssessment.coldStart(
          currentAmount, currency, "INVALID_AMOUNT", profileStatus);
    }

    if (!config.isEnabled()) {
      return FirstTimeHighValueAssessment.coldStart(
          currentAmount, currency, "DETECTION_DISABLED", profileStatus);
    }

    String reqCurrency = currency != null ? currency : "USD";
    OffsetDateTime evalTime = asOf != null ? asOf : OffsetDateTime.now();

    // 1. Filter history: COMPLETED only, strictly before evalTime, matching currency, excluding current tx
    List<Transaction> currencyHistory =
        (eligibleHistory == null ? List.<Transaction>of() : eligibleHistory).stream()
            .filter(t -> t.getStatus() != null && "COMPLETED".equalsIgnoreCase(t.getStatus().name()))
            .filter(t -> t.getAmount() != null && t.getAmount().signum() > 0)
            .filter(t -> matchesCurrency(t, reqCurrency))
            .filter(t -> excludeTransactionId == null || !excludeTransactionId.equals(t.getId()))
            .filter(t -> t.getTransactionTimestamp() != null && t.getTransactionTimestamp().isBefore(evalTime))
            .sorted(Comparator.comparing(Transaction::getTransactionTimestamp))
            .toList();

    int n = currencyHistory.size();

    // Cold-start guard: missing history is NOT fraud
    if (n < config.getMinTransactionsForBaseline()) {
      return FirstTimeHighValueAssessment.coldStart(
          currentAmount,
          reqCurrency,
          "Sample count " + n + " < minimum " + config.getMinTransactionsForBaseline(),
          profileStatus);
    }

    // 2. Rolling windows. Prefer the long-term window; fall back to the medium
    // window, then to the full eligible history. A short window is never used
    // as the only baseline.
    OffsetDateTime recentWindowStart =
        evalTime.minus(config.getRecentWindowDays(), ChronoUnit.DAYS);
    OffsetDateTime mediumWindowStart =
        evalTime.minus(config.getMediumWindowDays(), ChronoUnit.DAYS);
    OffsetDateTime longWindowStart =
        evalTime.minus(config.getLongWindowDays(), ChronoUnit.DAYS);

    List<Transaction> recentWindow =
        currencyHistory.stream()
            .filter(t -> t.getTransactionTimestamp().isAfter(recentWindowStart))
            .toList();
    List<Transaction> mediumWindow =
        currencyHistory.stream()
            .filter(t -> t.getTransactionTimestamp().isAfter(mediumWindowStart))
            .toList();
    List<Transaction> longWindow =
        currencyHistory.stream()
            .filter(t -> t.getTransactionTimestamp().isAfter(longWindowStart))
            .toList();

    List<Transaction> baselineTransactions;
    String baselineWindow;
    if (longWindow.size() >= config.getMinTransactionsForBaseline()) {
      baselineTransactions = longWindow;
      baselineWindow = config.getLongWindowDays() + "d";
    } else if (mediumWindow.size() >= config.getMinTransactionsForBaseline()) {
      baselineTransactions = mediumWindow;
      baselineWindow = config.getMediumWindowDays() + "d";
    } else {
      baselineTransactions = currencyHistory;
      baselineWindow = "ALL";
    }

    // Compute statistical baseline on sorted amounts
    List<BigDecimal> amounts =
        baselineTransactions.stream().map(Transaction::getAmount).sorted().toList();
    int baselineCount = amounts.size();

    BigDecimal median = computeMedian(amounts);
    BigDecimal historicalMax = amounts.get(baselineCount - 1);

    List<BigDecimal> deviations =
        amounts.stream().map(a -> a.subtract(median).abs()).sorted().toList();
    BigDecimal mad = computeMedian(deviations);

    BigDecimal p75 = computePercentile(amounts, 0.75);
    BigDecimal p90 = computePercentile(amounts, config.getPercentileHighValue());
    BigDecimal p95 = computePercentile(amounts, config.getPercentileExtreme());

    BigDecimal amountRatio =
        median != null && median.signum() > 0
            ? currentAmount.divide(median, 4, RoundingMode.HALF_UP)
            : BigDecimal.ONE;

    BigDecimal ratioOverHistoricalMax =
        historicalMax != null && historicalMax.signum() > 0
            ? currentAmount.divide(historicalMax, 4, RoundingMode.HALF_UP)
            : null;

    BigDecimal modifiedZScore = null;
    boolean isZeroMad = mad == null || mad.compareTo(config.getZeroMadThreshold()) <= 0;
    if (!isZeroMad && median != null) {
      modifiedZScore =
          currentAmount
              .subtract(median)
              .abs()
              .multiply(NORMAL_DIST_SCALE)
              .divide(mad, 4, RoundingMode.HALF_UP);
    }

    double percentileRank = calculatePercentileRank(amounts, currentAmount);

    // 3. Recipient-specific history
    List<Transaction> recipientHistory = List.of();
    if (recipientId != null) {
      recipientHistory =
          currencyHistory.stream()
              .filter(t -> t.getRecipient() != null && recipientId.equals(t.getRecipient().getId()))
              .toList();
    }

    int recipientPaymentCount = recipientHistory.size();
    boolean isNewRecipient = (recipientPaymentCount == 0);

    BigDecimal recipientMedian = null;
    BigDecimal recipientMax = null;
    BigDecimal ratioOverRecipientMedian = null;
    BigDecimal ratioOverRecipientMax = null;

    if (!isNewRecipient) {
      List<BigDecimal> recAmounts =
          recipientHistory.stream().map(Transaction::getAmount).sorted().toList();
      recipientMedian = computeMedian(recAmounts);
      recipientMax = recAmounts.get(recAmounts.size() - 1);

      if (recipientMedian != null && recipientMedian.signum() > 0) {
        ratioOverRecipientMedian = currentAmount.divide(recipientMedian, 4, RoundingMode.HALF_UP);
      }
      if (recipientMax != null && recipientMax.signum() > 0) {
        ratioOverRecipientMax = currentAmount.divide(recipientMax, 4, RoundingMode.HALF_UP);
      }
    }

    // 4. Recent-window escalation. The short window is only used to identify a
    // sudden jump; it is never the only baseline.
    boolean isRapidEscalation = false;
    BigDecimal recentMax = null;
    if (recentWindow.size() >= 2) {
      List<BigDecimal> recentAmounts =
          recentWindow.stream().map(Transaction::getAmount).sorted().toList();
      recentMax = recentAmounts.get(recentAmounts.size() - 1);
      if (recentMax.signum() > 0) {
        BigDecimal jumpFactor = currentAmount.divide(recentMax, 4, RoundingMode.HALF_UP);
        if (jumpFactor.compareTo(config.getRapidEscalationFactor()) >= 0
            && currentAmount.compareTo(p75) > 0) {
          isRapidEscalation = true;
        }
      }
    }

    // 5. Confidence calibration (sample count drives confidence; cold-start
    // users are never treated as suspicious).
    double confidence;
    if (n >= config.getEstablishedThreshold()) {
      confidence = config.getConfidenceEstablished();
    } else if (n >= config.getColdStartThreshold()) {
      confidence = config.getConfidenceInitializing();
    } else {
      confidence = config.getConfidenceDiscountColdStart();
    }

    // 6. Classification & Anomaly Decision
    // Case 1: Legitimate established high-value payment. If the amount is
    // within the user's historical maximum and modified-Z is not extreme, this
    // user routinely makes payments of this scale.
    boolean amountWithinHistoricalScale =
        currentAmount.compareTo(historicalMax) <= 0
            && (modifiedZScore == null
                || modifiedZScore.compareTo(config.getAmountWithinScaleZThreshold()) <= 0)
            && (!isZeroMad || amountRatio.compareTo(config.getMinMeaningfulDeviationRatio()) <= 0);

    if (amountWithinHistoricalScale && currentAmount.compareTo(p75) >= 0) {
      return FirstTimeHighValueAssessment.legitimateEstablished(
          currentAmount,
          reqCurrency,
          median,
          historicalMax,
          isNewRecipient,
          recipientPaymentCount,
          baselineWindow,
          profileStatus);
    }

    // Case 2: Normal payment within distribution
    boolean isNormalAmount =
        currentAmount.compareTo(p90) <= 0
            && (modifiedZScore == null
                || modifiedZScore.compareTo(config.getModifiedZSignificantThreshold()) < 0)
            && amountRatio.compareTo(config.getNormalAmountRatioCeiling()) <= 0;

    if (isNormalAmount && !isRapidEscalation) {
      return FirstTimeHighValueAssessment.normal(
          currentAmount,
          reqCurrency,
          median,
          mad,
          historicalMax,
          isNewRecipient,
          recipientPaymentCount,
          baselineWindow,
          profileStatus);
    }

    // High-value anomaly detection branches:
    boolean exceedsHistoricalMax =
        currentAmount.compareTo(
                historicalMax.multiply(config.getHistoricalMaxEscalationFactor()))
            > 0;
    boolean isSignificantDeviation =
        (!isZeroMad
                && modifiedZScore != null
                && modifiedZScore.compareTo(config.getModifiedZSignificantThreshold()) >= 0)
            || (isZeroMad
                && amountRatio.compareTo(config.getZeroMadSignificantRatio()) >= 0
                && currentAmount.compareTo(historicalMax) > 0);

    // A new recipient alone is not suspicious. The amount must also be
    // meaningfully outside the user's established distribution.
    boolean meaningfulAmountDeviation =
        isSignificantDeviation
            || exceedsHistoricalMax
            || (amountRatio.compareTo(config.getMinMeaningfulDeviationRatio()) >= 0
                && currentAmount.compareTo(p90) > 0);

    // Classification Category C: FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE
    if (isNewRecipient && meaningfulAmountDeviation) {
      List<String> factors = new ArrayList<>();
      factors.add("Recipient is new to account (0 previous completed payments)");
      factors.add("Amount " + currentAmount + " " + reqCurrency + " is " + amountRatio + "x historical median (" + median + " " + reqCurrency + ")");
      if (historicalMax != null) {
        factors.add("Historical maximum: " + historicalMax + " " + reqCurrency);
      }
      factors.add("90th percentile: " + p90 + " " + reqCurrency);

      String severity =
          (exceedsHistoricalMax
                  && amountRatio.compareTo(config.getNewRecipientCriticalRatio()) >= 0)
              ? "CRITICAL"
              : "HIGH";

      String evidence =
          String.format(
              "This payment of %s %s is substantially larger than your usual payments (median %s %s) and is being sent to a recipient you have not paid before.",
              currentAmount, reqCurrency, median, reqCurrency);

      return new FirstTimeHighValueAssessment(
          FirstTimeHighValueClassification.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE,
          true,
          severity,
          confidence,
          currentAmount,
          median,
          reqCurrency,
          median,
          mad,
          modifiedZScore,
          amountRatio,
          percentileRank,
          historicalMax,
          ratioOverHistoricalMax,
          true,
          0,
          null,
          null,
          null,
          null,
          baselineWindow,
          profileStatus,
          factors,
          evidence,
          "First-time payment to new recipient is high-value",
          evalTime);
    }

    // Classification Category B: FIRST_HIGH_VALUE_TO_RECIPIENT
    if (!isNewRecipient
        && recipientMax != null
        && recipientMedian != null
        && ratioOverRecipientMax != null
        && ratioOverRecipientMax.compareTo(config.getRecipientMaxEscalationFactor()) > 0
        && ratioOverRecipientMedian != null
        && ratioOverRecipientMedian.compareTo(config.getRecipientEscalationFactor()) >= 0
        && (currentAmount.compareTo(p75) >= 0
            || (modifiedZScore != null
                && modifiedZScore.compareTo(config.getRecipientMinModifiedZ()) >= 0))) {

      List<String> factors = new ArrayList<>();
      factors.add("Previously paid recipient " + recipientPaymentCount + " times");
      factors.add("Previous recipient maximum: " + recipientMax + " " + reqCurrency);
      factors.add("Previous recipient median: " + recipientMedian + " " + reqCurrency);
      factors.add("Current amount is " + ratioOverRecipientMedian + "x recipient median");

      String severity =
          ratioOverRecipientMax.compareTo(config.getRecipientCriticalRatio()) >= 0
              ? "HIGH"
              : "MEDIUM";
      String evidence =
          String.format(
              "Payment of %s %s is %sx the previous maximum payment of %s %s sent to this recipient.",
              currentAmount, reqCurrency, ratioOverRecipientMax, recipientMax, reqCurrency);

      return new FirstTimeHighValueAssessment(
          FirstTimeHighValueClassification.FIRST_HIGH_VALUE_TO_RECIPIENT,
          true,
          severity,
          confidence,
          currentAmount,
          recipientMedian,
          reqCurrency,
          median,
          mad,
          modifiedZScore,
          amountRatio,
          percentileRank,
          historicalMax,
          ratioOverHistoricalMax,
          false,
          recipientPaymentCount,
          recipientMedian,
          recipientMax,
          ratioOverRecipientMedian,
          ratioOverRecipientMax,
          baselineWindow,
          profileStatus,
          factors,
          evidence,
          "First high-value payment to this recipient",
          evalTime);
    }

    // Classification Category A: FIRST_HIGH_VALUE_PAYMENT
    if (exceedsHistoricalMax && isSignificantDeviation) {
      List<String> factors = new ArrayList<>();
      factors.add("Exceeds historical maximum of " + historicalMax + " " + reqCurrency);
      factors.add("Ratio over historical median: " + amountRatio + "x");
      if (modifiedZScore != null) {
        factors.add("Modified Z-score: " + modifiedZScore);
      }
      factors.add("No prior high-value payment history on record");

      String severity =
          (ratioOverHistoricalMax != null
              && ratioOverHistoricalMax.compareTo(config.getExtremeRatioOverMax()) >= 0)
              ? "CRITICAL"
              : "HIGH";

      String evidence =
          String.format(
              "First payment of this magnitude (%s %s) ever observed for your account; prior largest normal payment was %s %s.",
              currentAmount, reqCurrency, historicalMax, reqCurrency);

      return new FirstTimeHighValueAssessment(
          FirstTimeHighValueClassification.FIRST_HIGH_VALUE_PAYMENT,
          true,
          severity,
          confidence,
          currentAmount,
          median,
          reqCurrency,
          median,
          mad,
          modifiedZScore,
          amountRatio,
          percentileRank,
          historicalMax,
          ratioOverHistoricalMax,
          isNewRecipient,
          recipientPaymentCount,
          recipientMedian,
          recipientMax,
          ratioOverRecipientMedian,
          ratioOverRecipientMax,
          baselineWindow,
          profileStatus,
          factors,
          evidence,
          "First high-value payment for account",
          evalTime);
    }

    // Classification Category E: RAPID_AMOUNT_ESCALATION
    if (isRapidEscalation) {
      List<String> factors = new ArrayList<>();
      factors.add("Recent " + config.getRapidEscalationWindowDays() + "-day maximum was " + recentMax + " " + reqCurrency);
      factors.add("Current amount represents a sudden escalation over recent activity");

      String evidence =
          String.format(
              "Payment amount of %s %s represents an unusually rapid escalation compared to recent activity (recent max %s %s).",
              currentAmount, reqCurrency, recentMax, reqCurrency);

      return new FirstTimeHighValueAssessment(
          FirstTimeHighValueClassification.RAPID_AMOUNT_ESCALATION,
          true,
          "HIGH",
          confidence,
          currentAmount,
          recentMax,
          reqCurrency,
          median,
          mad,
          modifiedZScore,
          amountRatio,
          percentileRank,
          historicalMax,
          ratioOverHistoricalMax,
          isNewRecipient,
          recipientPaymentCount,
          recipientMedian,
          recipientMax,
          ratioOverRecipientMedian,
          ratioOverRecipientMax,
          config.getRapidEscalationWindowDays() + "d",
          profileStatus,
          factors,
          evidence,
          "Rapid payment amount escalation",
          evalTime);
    }

    // Classification Category D: SIGNIFICANT_AMOUNT_DEVIATION
    if (isSignificantDeviation) {
      List<String> factors = new ArrayList<>();
      factors.add("Amount is " + amountRatio + "x user median (" + median + " " + reqCurrency + ")");
      if (modifiedZScore != null) {
        factors.add("Modified Z-score: " + modifiedZScore);
      }
      factors.add("Top percentile: " + (percentileRank >= 0.95 ? "95th+" : "90th+"));

      String severity =
          (modifiedZScore != null && modifiedZScore.compareTo(config.getModifiedZExtremeThreshold()) >= 0)
              ? "CRITICAL"
              : "HIGH";

      String evidence =
          String.format(
              "Payment amount of %s %s deviates significantly from your established spending distribution (median %s %s).",
              currentAmount, reqCurrency, median, reqCurrency);

      return new FirstTimeHighValueAssessment(
          FirstTimeHighValueClassification.SIGNIFICANT_AMOUNT_DEVIATION,
          true,
          severity,
          confidence,
          currentAmount,
          median,
          reqCurrency,
          median,
          mad,
          modifiedZScore,
          amountRatio,
          percentileRank,
          historicalMax,
          ratioOverHistoricalMax,
          isNewRecipient,
          recipientPaymentCount,
          recipientMedian,
          recipientMax,
          ratioOverRecipientMedian,
          ratioOverRecipientMax,
          baselineWindow,
          profileStatus,
          factors,
          evidence,
          "Significant amount deviation",
          evalTime);
    }

    // Classification: AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM
    if (exceedsHistoricalMax) {
      List<String> factors = new ArrayList<>();
      factors.add("Current: " + currentAmount + " " + reqCurrency);
      factors.add("Previous maximum: " + historicalMax + " " + reqCurrency);
      factors.add("Ratio over maximum: " + ratioOverHistoricalMax + "x");

      String evidence =
          String.format(
              "Payment of %s %s exceeds your previous largest completed payment of %s %s.",
              currentAmount, reqCurrency, historicalMax, reqCurrency);

      return new FirstTimeHighValueAssessment(
          FirstTimeHighValueClassification.AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM,
          true,
          "MEDIUM",
          confidence,
          currentAmount,
          historicalMax,
          reqCurrency,
          median,
          mad,
          modifiedZScore,
          amountRatio,
          percentileRank,
          historicalMax,
          ratioOverHistoricalMax,
          isNewRecipient,
          recipientPaymentCount,
          recipientMedian,
          recipientMax,
          ratioOverRecipientMedian,
          ratioOverRecipientMax,
          baselineWindow,
          profileStatus,
          factors,
          evidence,
          "Amount exceeds historical maximum",
          evalTime);
    }

    // Default: normal payment
    return FirstTimeHighValueAssessment.normal(
        currentAmount,
        reqCurrency,
        median,
        mad,
        historicalMax,
        isNewRecipient,
        recipientPaymentCount,
        baselineWindow,
        profileStatus);
  }

  /**
   * Translates the assessment into a list of PaymentSignals for the evaluation pipeline.
   */
  public List<PaymentSignal> toSignals(FirstTimeHighValueAssessment assessment) {
    if (assessment == null || !assessment.detected()) {
      return List.of();
    }

    PaymentSignalType signalType = mapClassificationToSignalType(assessment.classification());
    if (signalType == null) {
      return List.of();
    }

    PaymentSignal signal =
        new PaymentSignal(
            signalType,
            true,
            SignalSource.BEHAVIORAL_PROFILE,
            null,
            assessment.evidence(),
            assessment.severity(),
            assessment.confidence(),
            assessment.amountRatio(),
            assessment.observedAmount(),
            assessment.expectedAmount(),
            assessment.baselineWindow(),
            assessment.contributingFactors());

    return List.of(signal);
  }

  private PaymentSignalType mapClassificationToSignalType(
      FirstTimeHighValueClassification classification) {
    return switch (classification) {
      case FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE -> PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE;
      case FIRST_HIGH_VALUE_PAYMENT -> PaymentSignalType.FIRST_HIGH_VALUE_PAYMENT;
      case FIRST_HIGH_VALUE_TO_RECIPIENT -> PaymentSignalType.FIRST_HIGH_VALUE_TO_RECIPIENT;
      case SIGNIFICANT_AMOUNT_DEVIATION -> PaymentSignalType.SIGNIFICANT_AMOUNT_DEVIATION;
      case AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM -> PaymentSignalType.AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM;
      case RAPID_AMOUNT_ESCALATION -> PaymentSignalType.RAPID_AMOUNT_ESCALATION;
      default -> null;
    };
  }

  private static boolean matchesCurrency(Transaction t, String currency) {
    return currency == null || currency.equalsIgnoreCase(t.getCurrency());
  }

  private static BigDecimal computeMedian(List<BigDecimal> sorted) {
    if (sorted.isEmpty()) {
      return null;
    }
    int size = sorted.size();
    int middle = size / 2;
    if (size % 2 == 1) {
      return sorted.get(middle).setScale(2, RoundingMode.HALF_UP);
    }
    return sorted.get(middle - 1)
        .add(sorted.get(middle))
        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal computePercentile(List<BigDecimal> sorted, double p) {
    if (sorted.isEmpty()) {
      return null;
    }
    int n = sorted.size();
    if (n == 1) {
      return sorted.get(0);
    }
    double index = p * (n - 1);
    int lower = (int) Math.floor(index);
    int upper = (int) Math.ceil(index);
    if (lower == upper) {
      return sorted.get(lower);
    }
    BigDecimal lowerVal = sorted.get(lower);
    BigDecimal upperVal = sorted.get(upper);
    BigDecimal weight = BigDecimal.valueOf(index - lower);
    return lowerVal.add(upperVal.subtract(lowerVal).multiply(weight)).setScale(2, RoundingMode.HALF_UP);
  }

  private static double calculatePercentileRank(List<BigDecimal> sorted, BigDecimal value) {
    if (sorted.isEmpty() || value == null) {
      return 0.0;
    }
    long countBelow = sorted.stream().filter(v -> v.compareTo(value) < 0).count();
    return (double) countBelow / sorted.size();
  }
}

