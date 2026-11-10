package com.guardpay.risk.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorrelationSignalNormalizerTest {

  private final CorrelationSignalNormalizer normalizer = new CorrelationSignalNormalizer();

  @Test
  void presentDeterministicSignalIsTrustedAndFamilied() {
    List<CorrelationSignal> signals =
        normalizer.normalize(
            List.of(
                new PaymentSignal(
                    PaymentSignalType.NEW_DEVICE,
                    true,
                    SignalSource.TRANSACTION_HISTORY,
                    null,
                    "New device.")));

    assertEquals(1, signals.size());
    CorrelationSignal signal = signals.get(0);
    assertTrue(signal.present());
    assertEquals(EvidenceFamily.DEVICE, signal.family());
    assertEquals(0.9, signal.confidence(), 0.000001);
    assertEquals(20, signal.originalPoints());
    assertFalse(signal.derived());
  }

  @Test
  void unavailableReasonIsNotTreatedAsAbsentOrPresent() {
    List<CorrelationSignal> signals =
        normalizer.normalize(
            List.of(
                new PaymentSignal(
                    PaymentSignalType.NEW_GEO,
                    false,
                    SignalSource.NETWORK,
                    "GEO_UNAVAILABLE",
                    "No trusted country.")));

    assertEquals(SignalAvailability.UNAVAILABLE, signals.get(0).availability());
    assertFalse(signals.get(0).present());
  }

  @Test
  void plainNegativeObservationIsAbsent() {
    List<CorrelationSignal> signals =
        normalizer.normalize(
            List.of(
                new PaymentSignal(
                    PaymentSignalType.NEW_GEO,
                    false,
                    SignalSource.TRANSACTION_HISTORY,
                    null,
                    "Known country.")));

    assertEquals(SignalAvailability.ABSENT, signals.get(0).availability());
  }

  @Test
  void aiConfidenceIsPreservedAndMissingConfidenceIsConservative() {
    List<CorrelationSignal> signals =
        normalizer.normalize(
            List.of(
                new PaymentSignal(
                    PaymentSignalType.SCAM_PATTERN,
                    true,
                    SignalSource.AI,
                    null,
                    "Scam pattern.",
                    "HIGH",
                    0.82),
                new PaymentSignal(
                    PaymentSignalType.IMPERSONATION,
                    true,
                    SignalSource.AI,
                    null,
                    "Impersonation.")));

    assertEquals(0.82, signals.get(0).confidence(), 0.000001);
    assertEquals(0.5, signals.get(1).confidence(), 0.000001);
  }

  @Test
  void duplicateCodesKeepTheStrongestObservation() {
    List<CorrelationSignal> signals =
        normalizer.normalize(
            List.of(
                new PaymentSignal(
                    PaymentSignalType.URGENT_LANGUAGE,
                    true,
                    SignalSource.AI,
                    null,
                    "Weak.",
                    "LOW",
                    0.55),
                new PaymentSignal(
                    PaymentSignalType.URGENT_LANGUAGE,
                    true,
                    SignalSource.PAYMENT_CONTEXT,
                    null,
                    "Strong.",
                    "HIGH",
                    0.95)));

    assertEquals(1, signals.size());
    assertEquals(0.95, signals.get(0).confidence(), 0.000001);
  }

  @Test
  void overallBehavioralDeviationIsMarkedDerived() {
    List<CorrelationSignal> signals =
        normalizer.normalize(
            List.of(
                new PaymentSignal(
                    PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION,
                    true,
                    SignalSource.BEHAVIORAL_PROFILE,
                    null,
                    "Multiple anomalies.",
                    "HIGH",
                    0.75)));

    assertTrue(signals.get(0).derived());
    assertEquals(EvidenceFamily.BEHAVIORAL_AGGREGATE, signals.get(0).family());
  }

  @Test
  void nullAndEmptyInputsAreSafe() {
    assertTrue(normalizer.normalize(null).isEmpty());
    assertTrue(normalizer.normalize(List.of()).isEmpty());
  }
}
