package com.guardpay.risk.correlation;

import com.guardpay.risk.RiskSignal;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts collected {@link PaymentSignal}s into {@link CorrelationSignal}s.
 *
 * <p>Normalization is lossless with respect to the underlying evidence: it
 * only adds a family, an availability classification, a confidence, and the
 * original deterministic weight. It never invents a signal, never turns an
 * unavailable observation into a present one, and never changes the engine's
 * own scoring.
 */
public final class CorrelationSignalNormalizer {

  /** Confidence assigned to a deterministic, backend-verified fact. */
  static final double DETERMINISTIC_CONFIDENCE = 0.9;

  /** Conservative confidence for evidence whose source did not calibrate it. */
  static final double UNCALIBRATED_CONFIDENCE = 0.5;

  /** Default weight for AI evidence codes that are not engine findings. */
  static final int AI_EVIDENCE_POINTS = 10;

  private static final Map<PaymentSignalType, EvidenceFamily> FAMILIES =
      new EnumMap<>(PaymentSignalType.class);

  static {
    FAMILIES.put(PaymentSignalType.UNUSUAL_AMOUNT, EvidenceFamily.AMOUNT);
    FAMILIES.put(PaymentSignalType.AMOUNT_DEVIATION, EvidenceFamily.AMOUNT);
    FAMILIES.put(PaymentSignalType.REPEATED_ATTEMPTS, EvidenceFamily.VELOCITY);
    FAMILIES.put(PaymentSignalType.VELOCITY_HOURLY, EvidenceFamily.VELOCITY);
    FAMILIES.put(PaymentSignalType.VELOCITY_DAILY, EvidenceFamily.VELOCITY);
    FAMILIES.put(PaymentSignalType.TRANSACTION_FREQUENCY_ANOMALY, EvidenceFamily.VELOCITY);
    FAMILIES.put(PaymentSignalType.SPLIT_PAYMENT, EvidenceFamily.VELOCITY);
    FAMILIES.put(PaymentSignalType.NEW_RECIPIENT, EvidenceFamily.RECIPIENT);
    FAMILIES.put(PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY, EvidenceFamily.RECIPIENT);
    FAMILIES.put(PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION, EvidenceFamily.RECIPIENT);
    FAMILIES.put(PaymentSignalType.UNKNOWN_MERCHANT, EvidenceFamily.MERCHANT);
    FAMILIES.put(PaymentSignalType.UNEXPECTED_INVOICE, EvidenceFamily.CONTENT);
    FAMILIES.put(PaymentSignalType.UNEXPECTED_INVOICE_CONTEXT, EvidenceFamily.CONTENT);
    FAMILIES.put(PaymentSignalType.SUSPICIOUS_URL, EvidenceFamily.CONTENT);
    FAMILIES.put(PaymentSignalType.SUSPICIOUS_URL_STRUCTURE, EvidenceFamily.CONTENT);
    FAMILIES.put(PaymentSignalType.URGENT_LANGUAGE, EvidenceFamily.CONTENT);
    FAMILIES.put(PaymentSignalType.MISSING_CONTEXT, EvidenceFamily.CONTENT);
    FAMILIES.put(PaymentSignalType.SOCIAL_ENGINEERING_DETECTED, EvidenceFamily.SOCIAL_ENGINEERING);
    FAMILIES.put(PaymentSignalType.IMPERSONATION, EvidenceFamily.SOCIAL_ENGINEERING);
    FAMILIES.put(PaymentSignalType.PRESSURE_TO_PAY, EvidenceFamily.SOCIAL_ENGINEERING);
    FAMILIES.put(PaymentSignalType.SECRECY_REQUEST, EvidenceFamily.SOCIAL_ENGINEERING);
    FAMILIES.put(PaymentSignalType.SUSPICIOUS_PAYMENT_INSTRUCTION, EvidenceFamily.SOCIAL_ENGINEERING);
    FAMILIES.put(PaymentSignalType.SCAM_PATTERN, EvidenceFamily.SOCIAL_ENGINEERING);
    FAMILIES.put(PaymentSignalType.KNOWN_RECIPIENT_SCAM, EvidenceFamily.SOCIAL_ENGINEERING);
    FAMILIES.put(PaymentSignalType.ACTIVE_CALL, EvidenceFamily.CALL);
    FAMILIES.put(PaymentSignalType.NEW_DEVICE, EvidenceFamily.DEVICE);
    FAMILIES.put(PaymentSignalType.DEVICE_BEHAVIOR_DEVIATION, EvidenceFamily.DEVICE);
    FAMILIES.put(PaymentSignalType.NEW_GEO, EvidenceFamily.GEOLOCATION);
    FAMILIES.put(PaymentSignalType.IMPOSSIBLE_TRAVEL, EvidenceFamily.GEOLOCATION);
    FAMILIES.put(PaymentSignalType.GEOGRAPHIC_BEHAVIOR_DEVIATION, EvidenceFamily.GEOLOCATION);
    FAMILIES.put(PaymentSignalType.VPN_PROXY, EvidenceFamily.NETWORK);
    FAMILIES.put(PaymentSignalType.BLOCKLISTED_RECIPIENT, EvidenceFamily.BLOCKLIST);
    FAMILIES.put(PaymentSignalType.UNUSUAL_PAYMENT_HOUR, EvidenceFamily.TIME);
    FAMILIES.put(PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION, EvidenceFamily.BEHAVIORAL_AGGREGATE);
  }

  /**
   * Reason codes that mean "this observation could not be made". Anything
   * else with {@code detected=false} is a genuine negative observation.
   */
  private static final Set<String> UNAVAILABLE_REASONS =
      Set.of(
          "NOT_AVAILABLE",
          "NOT_AVAILABLE_YET",
          "STALE",
          "INSUFFICIENT_DATA",
          "INSUFFICIENT_HISTORY",
          "INSUFFICIENT_CONTEXT",
          "CONTENT_UNAVAILABLE",
          "GEO_UNAVAILABLE",
          "USER_UNAVAILABLE",
          "NETWORK_REPUTATION_UNAVAILABLE",
          "RECIPIENT_IDENTITY_UNAVAILABLE",
          "MERCHANT_IDENTITY_UNAVAILABLE",
          "DEVICE_IDENTITY_UNAVAILABLE",
          "BLOCKLIST_UNAVAILABLE",
          "AMOUNT_UNAVAILABLE",
          "NO_PRIOR_DEVICE_HISTORY",
          "NO_PRIOR_GEO_HISTORY");

  public List<CorrelationSignal> normalize(List<PaymentSignal> signals) {
    if (signals == null || signals.isEmpty()) {
      return List.of();
    }
    Map<String, CorrelationSignal> byCode = new LinkedHashMap<>();
    for (PaymentSignal signal : signals) {
      if (signal == null || signal.type() == null) {
        continue;
      }
      CorrelationSignal normalized = normalizeOne(signal);
      byCode.merge(normalized.code(), normalized, CorrelationSignalNormalizer::prefer);
    }
    return List.copyOf(byCode.values());
  }

  static EvidenceFamily familyOf(PaymentSignalType type) {
    return FAMILIES.getOrDefault(type, EvidenceFamily.CONTENT);
  }

  private CorrelationSignal normalizeOne(PaymentSignal signal) {
    PaymentSignalType type = signal.type();
    String code = type.name();
    return new CorrelationSignal(
        code,
        familyOf(type),
        signal.source(),
        availabilityOf(signal),
        severityOf(signal),
        confidenceOf(signal),
        originalPoints(type),
        signal.evidence(),
        type == PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION);
  }

  private static SignalAvailability availabilityOf(PaymentSignal signal) {
    if (signal.detected()) {
      return SignalAvailability.PRESENT;
    }
    String reason = signal.reason();
    if (reason == null || reason.isBlank()) {
      return SignalAvailability.ABSENT;
    }
    String normalized = reason.toUpperCase(Locale.ROOT);
    if (UNAVAILABLE_REASONS.contains(normalized)) {
      return SignalAvailability.UNAVAILABLE;
    }
    if (normalized.contains("UNAVAILABLE")
        || normalized.contains("INSUFFICIENT")
        || normalized.startsWith("NO_")) {
      return SignalAvailability.UNKNOWN;
    }
    return SignalAvailability.ABSENT;
  }

  private static double confidenceOf(PaymentSignal signal) {
    if (signal.confidence() != null) {
      return clamp01(signal.confidence());
    }
    if (signal.source() == SignalSource.AI || signal.source() == SignalSource.BEHAVIORAL_PROFILE) {
      return UNCALIBRATED_CONFIDENCE;
    }
    return DETERMINISTIC_CONFIDENCE;
  }

  private static String severityOf(PaymentSignal signal) {
    if (signal.severity() != null && !signal.severity().isBlank()) {
      return signal.severity().toUpperCase(Locale.ROOT);
    }
    int points = originalPoints(signal.type());
    if (points >= 25) {
      return "HIGH";
    }
    if (points >= 15) {
      return "MEDIUM";
    }
    return "LOW";
  }

  static int originalPoints(PaymentSignalType type) {
    if (type == PaymentSignalType.UNUSUAL_AMOUNT) {
      return RiskSignal.LARGE_AMOUNT.points();
    }
    try {
      return RiskSignal.valueOf(type.name()).points();
    } catch (IllegalArgumentException notAnEngineSignal) {
      return AI_EVIDENCE_POINTS;
    }
  }

  private static CorrelationSignal prefer(CorrelationSignal left, CorrelationSignal right) {
    if (left.present() != right.present()) {
      return left.present() ? left : right;
    }
    return left.confidence() >= right.confidence() ? left : right;
  }

  private static double clamp01(double value) {
    if (value < 0.0) {
      return 0.0;
    }
    return Math.min(value, 1.0);
  }
}
