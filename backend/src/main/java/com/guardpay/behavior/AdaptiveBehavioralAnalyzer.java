package com.guardpay.behavior;

import com.guardpay.behavior.dto.BehavioralAnalysisRequest;
import com.guardpay.behavior.dto.BehavioralAnalysisResponse;
import com.guardpay.behavior.dto.BehavioralFindingDto;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Adaptive behavioral analyzer: compares an incoming payment against the
 * user's learned behavioral profile and produces anomaly findings.
 *
 * <p>This is EVIDENCE ONLY. It never approves or rejects payments, never
 * overrides the deterministic engine, and never bypasses hard-stop rules.
 * Findings are folded into existing engine signals where they overlap,
 * or added as complementary behavioral signals where they do not.
 */
@Service
public class AdaptiveBehavioralAnalyzer {

  private static final int MIN_CONFIDENCE_SAMPLES = 5;

  private final BehavioralProfileService profileService;

  public AdaptiveBehavioralAnalyzer(BehavioralProfileService profileService) {
    this.profileService = profileService;
  }

  /**
   * Analyzes an incoming payment context against the user's behavioral
   * profile and returns complementary PaymentSignal objects.
   *
   * <p>Returns empty list when profiling is unavailable or insufficient
   * data exists — never fabricates findings.
   */
  public List<PaymentSignal> analyze(BehavioralAnalysisRequest request) {
    if (request.userId() == null) {
      return List.of();
    }

    UUID excludeTxId = request.transactionId() != null ? request.transactionId() : null;
    BehavioralAnalysisResponse response =
        profileService.analyze(request, excludeTxId);

    if (response.available() == null || !response.available()) {
      return List.of();
    }

    List<PaymentSignal> signals = new ArrayList<>();

    for (BehavioralFindingDto finding : response.findings()) {
      PaymentSignalType type = mapCode(finding.code());
      if (type == null) {
        continue;
      }

      boolean detected = !response.profileStatus().equals("INSUFFICIENT_DATA")
          && (finding.confidence() != null && finding.confidence() >= 0.6);

      String reason = null;
      if (!detected) {
        reason = "INSUFFICIENT_PROFILE_DATA";
      }

      signals.add(
          new PaymentSignal(
              type,
              detected,
              SignalSource.BEHAVIORAL_PROFILE,
              reason,
              finding.evidence(),
              finding.severity(),
              finding.confidence(),
              finding.deviationRatio(),
              finding.observedValue(),
              finding.expectedValue(),
              finding.profileWindow(),
              finding.contributingFactors()));
    }

    // Overall behavioral deviation signal: fires when multiple anomalies co-occur
    if (!signals.isEmpty()
        && response.overallBehavioralDeviation() != null
        && (response.overallBehavioralDeviation().equals("MODERATE")
            || response.overallBehavioralDeviation().equals("SIGNIFICANT"))) {
      signals.add(
          new PaymentSignal(
              PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION,
              true,
              SignalSource.BEHAVIORAL_PROFILE,
              null,
              "Multiple behavioral anomalies detected across amount, time, recipient, device, or location dimensions.",
              BehavioralSeverityMap.toSeverity(response.overallBehavioralDeviation()),
              0.75,
              null,
              null,
              null,
              null,
              signals.stream()
                  .filter(PaymentSignal::detected)
                  .map(signal -> signal.type().name())
                  .toList()));
    }

    return List.copyOf(signals);
  }

  private PaymentSignalType mapCode(String code) {
    return switch (code) {
      case "AMOUNT_DEVIATION" -> PaymentSignalType.AMOUNT_DEVIATION;
      case "UNUSUAL_PAYMENT_HOUR" -> PaymentSignalType.UNUSUAL_PAYMENT_HOUR;
      case "RECIPIENT_BEHAVIOR_DEVIATION" -> PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION;
      case "TRANSACTION_FREQUENCY_ANOMALY" -> PaymentSignalType.TRANSACTION_FREQUENCY_ANOMALY;
      case "GEOGRAPHIC_BEHAVIOR_DEVIATION" -> PaymentSignalType.GEOGRAPHIC_BEHAVIOR_DEVIATION;
      case "DEVICE_BEHAVIOR_DEVIATION" -> PaymentSignalType.DEVICE_BEHAVIOR_DEVIATION;
      case "FIRST_HIGH_VALUE_PAYMENT" -> PaymentSignalType.FIRST_HIGH_VALUE_PAYMENT;
      case "FIRST_HIGH_VALUE_TO_RECIPIENT" -> PaymentSignalType.FIRST_HIGH_VALUE_TO_RECIPIENT;
      case "FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE" -> PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE;
      case "SIGNIFICANT_AMOUNT_DEVIATION" -> PaymentSignalType.SIGNIFICANT_AMOUNT_DEVIATION;
      case "AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM" -> PaymentSignalType.AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM;
      case "RAPID_AMOUNT_ESCALATION" -> PaymentSignalType.RAPID_AMOUNT_ESCALATION;
      default -> null;
    };
  }

  /** Maps overall deviation to a severity string for the signal. */
  enum BehavioralSeverityMap {
    MODERATE(BehavioralSeverity.MEDIUM.name()),
    SIGNIFICANT(BehavioralSeverity.HIGH.name());

    private final String severity;

    BehavioralSeverityMap(String severity) {
      this.severity = severity;
    }

    static String toSeverity(String deviation) {
      return switch (deviation) {
        case "MODERATE" -> BehavioralSeverity.MEDIUM.name();
        case "SIGNIFICANT" -> BehavioralSeverity.HIGH.name();
        default -> BehavioralSeverity.INFO.name();
      };
    }
  }
}
