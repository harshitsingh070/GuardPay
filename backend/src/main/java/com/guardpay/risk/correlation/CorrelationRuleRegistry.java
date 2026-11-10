package com.guardpay.risk.correlation;

import com.guardpay.risk.signal.PaymentSignalType;
import java.util.List;

/**
 * The versioned correlation rule catalogue.
 *
 * <p>Every rule maps onto signals the collector can actually produce today.
 * Combinations that need data GuardPay does not collect are listed in
 * {@link #unsupported()} instead of being approximated silently.
 */
public final class CorrelationRuleRegistry {

  public static final String RULE_VERSION = "1.0";

  private static final double MIN_INTERACTION = 0.12;
  private static final double MIN_CONFIDENCE = 0.5;
  private static final int MAX_CONTRIBUTION = 25;

  private final List<CorrelationRule> rules;
  private final List<UnsupportedCorrelation> unsupported;

  public CorrelationRuleRegistry() {
    this(defaultRules(), unsupportedCorrelations());
  }

  public CorrelationRuleRegistry(
      List<CorrelationRule> rules, List<UnsupportedCorrelation> unsupported) {
    this.rules = List.copyOf(rules);
    this.unsupported = List.copyOf(unsupported);
  }

  public static CorrelationRuleRegistry defaults() {
    return new CorrelationRuleRegistry();
  }

  public List<CorrelationRule> rules() {
    return rules;
  }

  public List<CorrelationRule> enabledRules() {
    return rules.stream().filter(CorrelationRule::enabled).toList();
  }

  public List<UnsupportedCorrelation> unsupported() {
    return unsupported;
  }

  private static List<CorrelationRule> defaultRules() {
    return List.of(
        rule(
            "ACCOUNT_TAKEOVER_COMBO",
            CorrelationCategory.ACCOUNT_TAKEOVER,
            "Account takeover combination",
            "A new device, an unfamiliar location, and an unfamiliar recipient appear together.",
            1.0,
            req(EvidenceFamily.DEVICE, PaymentSignalType.NEW_DEVICE, PaymentSignalType.DEVICE_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.GEOLOCATION,
                PaymentSignalType.NEW_GEO,
                PaymentSignalType.GEOGRAPHIC_BEHAVIOR_DEVIATION,
                PaymentSignalType.IMPOSSIBLE_TRAVEL),
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.NEW_RECIPIENT,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION)),
        rule(
            "ACCOUNT_TAKEOVER_VELOCITY_COMBO",
            CorrelationCategory.ACCOUNT_TAKEOVER,
            "New device with amount and velocity anomaly",
            "A new device initiates an unusual amount during a burst of payment attempts.",
            0.95,
            req(EvidenceFamily.DEVICE, PaymentSignalType.NEW_DEVICE, PaymentSignalType.DEVICE_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.AMOUNT,
                PaymentSignalType.UNUSUAL_AMOUNT,
                PaymentSignalType.AMOUNT_DEVIATION,
                PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY),
            req(
                EvidenceFamily.VELOCITY,
                PaymentSignalType.REPEATED_ATTEMPTS,
                PaymentSignalType.VELOCITY_HOURLY,
                PaymentSignalType.VELOCITY_DAILY,
                PaymentSignalType.TRANSACTION_FREQUENCY_ANOMALY)),
        rule(
            "ACCOUNT_TAKEOVER_ATTEMPT_COMBO",
            CorrelationCategory.ACCOUNT_TAKEOVER,
            "New device with repeated attempts",
            "A new device retries payments to an unfamiliar recipient within a short window.",
            0.9,
            req(EvidenceFamily.DEVICE, PaymentSignalType.NEW_DEVICE, PaymentSignalType.DEVICE_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.VELOCITY,
                PaymentSignalType.REPEATED_ATTEMPTS,
                PaymentSignalType.VELOCITY_HOURLY,
                PaymentSignalType.VELOCITY_DAILY),
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.NEW_RECIPIENT,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION)),
        rule(
            "SOCIAL_ENGINEERING_CALL_COMBO",
            CorrelationCategory.SOCIAL_ENGINEERING,
            "Call-guided payment to a new recipient",
            "An active call, an unfamiliar recipient, and urgent or social-engineering language co-occur.",
            1.0,
            req(EvidenceFamily.CALL, PaymentSignalType.ACTIVE_CALL),
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.NEW_RECIPIENT,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.CONTENT,
                PaymentSignalType.URGENT_LANGUAGE,
                PaymentSignalType.PRESSURE_TO_PAY,
                PaymentSignalType.IMPERSONATION,
                PaymentSignalType.SOCIAL_ENGINEERING_DETECTED,
                PaymentSignalType.SCAM_PATTERN)),
        rule(
            "SOCIAL_ENGINEERING_RECIPIENT_COMBO",
            CorrelationCategory.SOCIAL_ENGINEERING,
            "Scam instruction to a new recipient",
            "A new recipient, an unusual amount, and a suspicious payment instruction appear together.",
            1.0,
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.NEW_RECIPIENT,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.AMOUNT,
                PaymentSignalType.UNUSUAL_AMOUNT,
                PaymentSignalType.AMOUNT_DEVIATION,
                PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY),
            req(
                EvidenceFamily.SOCIAL_ENGINEERING,
                PaymentSignalType.SUSPICIOUS_PAYMENT_INSTRUCTION,
                PaymentSignalType.IMPERSONATION,
                PaymentSignalType.SCAM_PATTERN,
                PaymentSignalType.KNOWN_RECIPIENT_SCAM,
                PaymentSignalType.SOCIAL_ENGINEERING_DETECTED,
                PaymentSignalType.PRESSURE_TO_PAY)),
        rule(
            "PHISHING_CONTENT_COMBO",
            CorrelationCategory.SOCIAL_ENGINEERING,
            "Phishing content combination",
            "A suspicious URL, urgent language, and a payment instruction appear in the same request.",
            0.95,
            req(
                EvidenceFamily.CONTENT,
                PaymentSignalType.SUSPICIOUS_URL,
                PaymentSignalType.SUSPICIOUS_URL_STRUCTURE),
            req(
                EvidenceFamily.CONTENT,
                PaymentSignalType.URGENT_LANGUAGE,
                PaymentSignalType.PRESSURE_TO_PAY),
            req(
                EvidenceFamily.SOCIAL_ENGINEERING,
                PaymentSignalType.SUSPICIOUS_PAYMENT_INSTRUCTION,
                PaymentSignalType.IMPERSONATION,
                PaymentSignalType.SCAM_PATTERN,
                PaymentSignalType.SOCIAL_ENGINEERING_DETECTED)),
        rule(
            "VELOCITY_AMOUNT_COMBO",
            CorrelationCategory.VELOCITY_STRUCTURING,
            "Repeated attempts with unusual amount",
            "Payment attempts repeat while the amount is well outside the learned pattern.",
            0.85,
            req(
                EvidenceFamily.VELOCITY,
                PaymentSignalType.REPEATED_ATTEMPTS,
                PaymentSignalType.VELOCITY_HOURLY,
                PaymentSignalType.TRANSACTION_FREQUENCY_ANOMALY),
            req(
                EvidenceFamily.AMOUNT,
                PaymentSignalType.UNUSUAL_AMOUNT,
                PaymentSignalType.AMOUNT_DEVIATION,
                PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY)),
        rule(
            "SPLIT_PAYMENT_RECIPIENT_COMBO",
            CorrelationCategory.VELOCITY_STRUCTURING,
            "Split payments to an anomalous recipient",
            "Several sub-threshold payments go to a recipient that is itself anomalous.",
            0.9,
            req(EvidenceFamily.VELOCITY, PaymentSignalType.SPLIT_PAYMENT),
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION)),
        rule(
            "NEW_RECIPIENT_AMOUNT_COMBO",
            CorrelationCategory.COUNTERPARTY_RISK,
            "New recipient with anomalous amount",
            "A first-time recipient receives an amount outside both the user's and the recipient's pattern.",
            0.85,
            req(EvidenceFamily.RECIPIENT, PaymentSignalType.NEW_RECIPIENT),
            req(
                EvidenceFamily.AMOUNT,
                PaymentSignalType.UNUSUAL_AMOUNT,
                PaymentSignalType.AMOUNT_DEVIATION),
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION)),
        rule(
            "VPN_DEVICE_GEO_COMBO",
            CorrelationCategory.NETWORK_ANOMALY,
            "Anonymized network with device and location change",
            "A VPN or proxy is combined with a new device and an unfamiliar location.",
            0.9,
            req(EvidenceFamily.NETWORK, PaymentSignalType.VPN_PROXY),
            req(EvidenceFamily.DEVICE, PaymentSignalType.NEW_DEVICE, PaymentSignalType.DEVICE_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.GEOLOCATION,
                PaymentSignalType.NEW_GEO,
                PaymentSignalType.GEOGRAPHIC_BEHAVIOR_DEVIATION,
                PaymentSignalType.IMPOSSIBLE_TRAVEL)),
        rule(
            "AMOUNT_TIME_FREQUENCY_COMBO",
            CorrelationCategory.BEHAVIORAL_SHIFT,
            "Amount, timing, and frequency shift",
            "Amount, time of day, and payment frequency all deviate from the learned baseline.",
            0.8,
            req(
                EvidenceFamily.AMOUNT,
                PaymentSignalType.UNUSUAL_AMOUNT,
                PaymentSignalType.AMOUNT_DEVIATION,
                PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY),
            req(EvidenceFamily.TIME, PaymentSignalType.UNUSUAL_PAYMENT_HOUR),
            req(
                EvidenceFamily.VELOCITY,
                PaymentSignalType.TRANSACTION_FREQUENCY_ANOMALY,
                PaymentSignalType.REPEATED_ATTEMPTS,
                PaymentSignalType.VELOCITY_HOURLY)),
        rule(
            "AMOUNT_RECIPIENT_GEO_COMBO",
            CorrelationCategory.COMBINED_DEVIATION,
            "Amount, recipient, and location deviation",
            "An unusual amount goes to an unfamiliar recipient from an unfamiliar location.",
            0.85,
            req(
                EvidenceFamily.AMOUNT,
                PaymentSignalType.UNUSUAL_AMOUNT,
                PaymentSignalType.AMOUNT_DEVIATION,
                PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY),
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.NEW_RECIPIENT,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.GEOLOCATION,
                PaymentSignalType.NEW_GEO,
                PaymentSignalType.GEOGRAPHIC_BEHAVIOR_DEVIATION,
                PaymentSignalType.IMPOSSIBLE_TRAVEL)),
        rule(
            "BEHAVIORAL_MULTI_DIMENSION_COMBO",
            CorrelationCategory.BEHAVIORAL_SHIFT,
            "Multi-dimension behavioral deviation",
            "Device, recipient, and the overall behavioral profile all deviate at once.",
            0.9,
            req(EvidenceFamily.DEVICE, PaymentSignalType.NEW_DEVICE, PaymentSignalType.DEVICE_BEHAVIOR_DEVIATION),
            req(
                EvidenceFamily.RECIPIENT,
                PaymentSignalType.NEW_RECIPIENT,
                PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION),
            req(EvidenceFamily.BEHAVIORAL_AGGREGATE, PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION)));
  }

  private static List<UnsupportedCorrelation> unsupportedCorrelations() {
    return List.of(
        new UnsupportedCorrelation(
            "MULTIPLE_NEW_RECIPIENTS_VELOCITY",
            "The collector reports whether this recipient is new, not how many distinct new "
                + "recipients were added inside a window."),
        new UnsupportedCorrelation(
            "REPEATED_FAILED_ATTEMPTS",
            "Velocity signals count attempts of any status; failed-attempt counts are not "
                + "collected separately."),
        new UnsupportedCorrelation(
            "DATACENTER_ASN_VELOCITY",
            "The ASN is captured on the transaction but there is no hosting/datacenter "
                + "reputation classification to correlate."),
        new UnsupportedCorrelation(
            "AMOUNT_ESCALATION_SEQUENCE",
            "Signals are evaluated per payment; there is no ordered amount-escalation series."),
        new UnsupportedCorrelation(
            "MULTIPLE_RECIPIENT_CHANGES",
            "There is no recipient-change history signal; only first-seen and per-recipient "
                + "deviation are available."),
        new UnsupportedCorrelation(
            "SHORT_TIME_INTERVAL_CHANGES",
            "Inter-arrival timing between counterparty changes is not recorded."),
        new UnsupportedCorrelation(
            "RECIPIENT_HISTORY_MULTI_ANOMALY",
            "Only one recipient anomaly signal exists (amount or behavioral), so a "
                + "multi-anomaly recipient combination cannot be formed independently."));
  }

  private static CorrelationRule rule(
      String code,
      CorrelationCategory category,
      String title,
      String description,
      double weight,
      SignalRequirement... requirements) {
    return new CorrelationRule(
        code,
        category,
        title,
        description,
        List.of(requirements),
        weight,
        MIN_INTERACTION,
        MIN_CONFIDENCE,
        MAX_CONTRIBUTION,
        RULE_VERSION,
        true);
  }

  private static SignalRequirement req(EvidenceFamily family, PaymentSignalType... types) {
    return new SignalRequirement(
        family, java.util.Arrays.stream(types).map(Enum::name).toList());
  }
}
