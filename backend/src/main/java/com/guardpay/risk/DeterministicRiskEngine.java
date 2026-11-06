package com.guardpay.risk;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Deterministic GuardPay risk engine — the final decision layer.
 *
 * <p>Pure business component: no controllers, no HTTP, no repositories, no
 * AI, no PayPal. Same input always yields the same output. AI findings (future
 * milestone) arrive as the plain {@code socialEngineeringDetected} flag and
 * can only contribute evidence weight — never override the score or action.
 */
@Service
public class DeterministicRiskEngine {

  /**
   * A large amount means {@code currentAmount >= historicalAverage * 5}.
   * Null, zero, or negative history means "insufficient data" and never fires.
   *
   * <p>Public so the automatic signal collector reuses the exact same
   * definition — the value itself (the rule) must never be duplicated.
   */
  public static final int LARGE_AMOUNT_MULTIPLIER = 5;

  /**
   * Velocity rule: making at least this many payment attempts (counting the
   * current attempt) inside {@link #REPEATED_ATTEMPT_WINDOW_MINUTES} fires the
   * repeated-attempts signal. Public so the signal collector counts using the
   * exact same rule instead of duplicating the numbers.
   */
  public static final int REPEATED_ATTEMPT_THRESHOLD = 3;

  public static final int REPEATED_ATTEMPT_WINDOW_MINUTES = 10;

  public RiskEvaluationResult evaluate(RiskEvaluationRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    List<RiskFinding> findings = new ArrayList<>();
    findings.add(finding(RiskSignal.ACTIVE_CALL, request.activeCall()));
    // NEW_RECIPIENT is a single signal: the supplied flag already covers the
    // "no previous history" case, so history must not add points a second time.
    findings.add(finding(RiskSignal.NEW_RECIPIENT, request.newRecipient()));
    findings.add(finding(RiskSignal.LARGE_AMOUNT, isLargeAmount(request)));
    findings.add(
        finding(RiskSignal.RECIPIENT_AMOUNT_ANOMALY, request.recipientAmountAnomaly()));
    findings.add(finding(RiskSignal.REPEATED_ATTEMPTS, request.repeatedAttempts()));
    findings.add(finding(RiskSignal.VELOCITY_HOURLY, request.hourlyVelocity()));
    findings.add(finding(RiskSignal.VELOCITY_DAILY, request.dailyVelocity()));
    findings.add(finding(RiskSignal.BLOCKLISTED_RECIPIENT, request.blocklistedRecipient()));
    findings.add(finding(RiskSignal.UNKNOWN_MERCHANT, request.unknownMerchant()));
    findings.add(finding(RiskSignal.UNEXPECTED_INVOICE, request.unexpectedInvoice()));
    findings.add(finding(RiskSignal.URGENT_LANGUAGE, request.urgentLanguage()));
    findings.add(finding(RiskSignal.SUSPICIOUS_URL, request.suspiciousUrl()));
    findings.add(finding(RiskSignal.SOCIAL_ENGINEERING, request.socialEngineeringDetected()));
    findings.add(finding(RiskSignal.KNOWN_RECIPIENT_SCAM, request.knownRecipientScam()));
    findings.add(finding(RiskSignal.SPLIT_PAYMENT, request.splitPayment()));
    findings.add(finding(RiskSignal.MISSING_CONTEXT, request.missingContext()));
    findings.add(finding(RiskSignal.NEW_DEVICE, request.newDevice()));
    findings.add(finding(RiskSignal.NEW_GEO, request.newGeo()));
    findings.add(finding(RiskSignal.IMPOSSIBLE_TRAVEL, request.impossibleTravel()));
    findings.add(finding(RiskSignal.VPN_PROXY, request.vpnProxy()));
    findings.add(finding(RiskSignal.AMOUNT_DEVIATION, request.amountDeviation()));
    findings.add(finding(RiskSignal.UNUSUAL_PAYMENT_HOUR, request.unusualPaymentHour()));
    findings.add(finding(RiskSignal.RECIPIENT_BEHAVIOR_DEVIATION, request.recipientBehaviorDeviation()));
    findings.add(finding(RiskSignal.TRANSACTION_FREQUENCY_ANOMALY, request.transactionFrequencyAnomaly()));
    findings.add(finding(RiskSignal.GEOGRAPHIC_BEHAVIOR_DEVIATION, request.geographicBehaviorDeviation()));
    findings.add(finding(RiskSignal.DEVICE_BEHAVIOR_DEVIATION, request.deviceBehaviorDeviation()));
    findings.add(finding(RiskSignal.OVERALL_BEHAVIOR_DEVIATION, request.overallBehaviorDeviation()));

    int total = findings.stream().filter(RiskFinding::triggered).mapToInt(RiskFinding::points).sum();
    boolean hardBlocked = request.blocklistedRecipient();
    int score = hardBlocked ? RiskLevels.MAX_SCORE : Math.min(total, RiskLevels.MAX_SCORE);
    RiskLevel riskLevel = RiskLevels.levelFor(score);

    return new RiskEvaluationResult(
        score,
        riskLevel,
        RiskLevels.actionFor(riskLevel),
        List.copyOf(findings),
        buildExplanation(findings, hardBlocked));
  }

  private boolean isLargeAmount(RiskEvaluationRequest request) {
    BigDecimal current = request.currentAmount();
    BigDecimal average = request.historicalAverage();
    if (current == null || average == null || average.signum() <= 0) {
      return false;
    }
    return current.compareTo(average.multiply(BigDecimal.valueOf(LARGE_AMOUNT_MULTIPLIER))) >= 0;
  }

  private RiskFinding finding(RiskSignal signal, boolean triggered) {
    return new RiskFinding(signal.code(), signal.title(), signal.description(), signal.points(), triggered);
  }

  private String buildExplanation(List<RiskFinding> findings, boolean hardBlocked) {
    List<String> reasons =
        findings.stream().filter(RiskFinding::triggered).map(RiskFinding::description).toList();
    if (hardBlocked) {
      return "GuardPay blocked this payment because the recipient is on your fraud blocklist.";
    }
    if (reasons.isEmpty()) {
      return "No unusual risk signals were detected for this payment.";
    }
    return "GuardPay flagged this payment for the following reasons: "
        + reasons.stream().collect(Collectors.joining(" "));
  }
}
