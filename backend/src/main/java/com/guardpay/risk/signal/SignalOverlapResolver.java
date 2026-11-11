package com.guardpay.risk.signal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collapses signals that describe the same underlying fact so the deterministic
 * engine and the correlation layer do not count one observation several times.
 *
 * <p>Each exclusive family keeps exactly one winner, in priority order. Signals
 * outside a family are left untouched. The resolver only suppresses signals
 * that are currently detected; the returned copies are marked
 * {@code detected=false} and carry {@link #SUPPRESSED_REASON} for audit.
 */
public final class SignalOverlapResolver {

  public static final String SUPPRESSED_REASON = "SUPPRESSED_OVERLAP";

  private static final List<List<String>> EXCLUSIVE_FAMILIES =
      List.of(
          // One learned amount-anomaly explanation per payment. LARGE_AMOUNT is
          // deliberately separate: it is the coarse 5x historical-average rule,
          // while AMOUNT_DEVIATION is the learned behavioral baseline. Both are
          // independent evidence and the benchmark expects them to coexist.
          List.of(
              "FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE",
              "FIRST_HIGH_VALUE_PAYMENT",
              "FIRST_HIGH_VALUE_TO_RECIPIENT",
              "SIGNIFICANT_AMOUNT_DEVIATION",
              "RECIPIENT_AMOUNT_ANOMALY",
              "AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM",
              "RAPID_AMOUNT_ESCALATION",
              "AMOUNT_DEVIATION"),
          // "New" already explains the behavioral deviation for the same dimension.
          List.of("NEW_RECIPIENT", "RECIPIENT_BEHAVIOR_DEVIATION"),
          List.of("NEW_DEVICE", "DEVICE_BEHAVIOR_DEVIATION"),
          List.of("NEW_GEO", "GEOGRAPHIC_BEHAVIOR_DEVIATION"),
          // Velocity variants are one burst, not three independent events.
          List.of("VELOCITY_DAILY", "VELOCITY_HOURLY", "REPEATED_ATTEMPTS"));

  /**
   * A first payment to a recipient already implies "new recipient"; keeping both
   * would add the cold-start penalty twice.
   */
  private static final Set<String> NEW_RECIPIENT_IMPLIED_BY =
      Set.of("FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE");

  /**
   * Overall behavioral deviation is a summary of the dimension-specific
   * deviations; it only adds information when none of them fired.
   */
  private static final Set<String> OVERALL_BEHAVIOR_SUPPRESSORS =
      Set.of(
          "AMOUNT_DEVIATION",
          "UNUSUAL_PAYMENT_HOUR",
          "RECIPIENT_BEHAVIOR_DEVIATION",
          "TRANSACTION_FREQUENCY_ANOMALY",
          "GEOGRAPHIC_BEHAVIOR_DEVIATION",
          "DEVICE_BEHAVIOR_DEVIATION",
          "NEW_RECIPIENT",
          "NEW_DEVICE",
          "NEW_GEO",
          "FIRST_HIGH_VALUE_PAYMENT",
          "FIRST_HIGH_VALUE_TO_RECIPIENT",
          "FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE",
          "SIGNIFICANT_AMOUNT_DEVIATION",
          "AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM",
          "RAPID_AMOUNT_ESCALATION",
          "LARGE_AMOUNT",
          "RECIPIENT_AMOUNT_ANOMALY");

  private SignalOverlapResolver() {}

  public static List<PaymentSignal> resolve(List<PaymentSignal> signals) {
    if (signals == null || signals.isEmpty()) {
      return List.of();
    }
    Set<String> detectedCodes = new LinkedHashSet<>();
    for (PaymentSignal signal : signals) {
      if (signal != null && signal.detected()) {
        detectedCodes.add(signal.type().name());
      }
    }
    Set<String> suppressed = suppressed(detectedCodes);
    if (suppressed.isEmpty()) {
      return List.copyOf(signals);
    }
    List<PaymentSignal> resolved = new ArrayList<>(signals.size());
    for (PaymentSignal signal : signals) {
      if (signal != null && suppressed.contains(signal.type().name())) {
        resolved.add(
            new PaymentSignal(
                signal.type(),
                false,
                signal.source(),
                SUPPRESSED_REASON,
                signal.evidence()));
      } else {
        resolved.add(signal);
      }
    }
    return List.copyOf(resolved);
  }

  /**
   * Returns the detected codes that must be suppressed. Exposed for the
   * deterministic engine, which resolves its own {@code RiskFinding} list.
   */
  public static Set<String> suppressed(Set<String> detectedCodes) {
    if (detectedCodes == null || detectedCodes.isEmpty()) {
      return Set.of();
    }
    Set<String> suppressed = new HashSet<>();

    for (List<String> family : EXCLUSIVE_FAMILIES) {
      String winner = null;
      for (String code : family) {
        if (detectedCodes.contains(code)) {
          winner = code;
          break;
        }
      }
      if (winner == null) {
        continue;
      }
      for (String code : family) {
        if (!code.equals(winner) && detectedCodes.contains(code)) {
          suppressed.add(code);
        }
      }
    }

    for (String implied : NEW_RECIPIENT_IMPLIED_BY) {
      if (detectedCodes.contains(implied)) {
        suppressed.add("NEW_RECIPIENT");
      }
    }

    if (detectedCodes.contains("OVERALL_BEHAVIOR_DEVIATION")) {
      for (String specific : OVERALL_BEHAVIOR_SUPPRESSORS) {
        if (detectedCodes.contains(specific)) {
          suppressed.add("OVERALL_BEHAVIOR_DEVIATION");
          break;
        }
      }
    }
    return Set.copyOf(suppressed);
  }
}
