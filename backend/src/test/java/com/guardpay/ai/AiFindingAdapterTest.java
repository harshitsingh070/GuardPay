package com.guardpay.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.ai.dto.AiFindingDto;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Adapter boundary: controlled codes convert, everything else is dropped
 * loudly (never converted, never silent about the policy).
 */
class AiFindingAdapterTest {

  private static AiFindingDto finding(String code, Double confidence) {
    return new AiFindingDto(code, "Title", "Description", "HIGH", confidence, "Evidence text");
  }

  @Test
  void validFindingsBecomeAiSignals() {
    List<PaymentSignal> signals =
        AiFindingAdapter.toPaymentSignals(
            List.of(finding("URGENT_LANGUAGE", 0.9), finding("IMPERSONATION", 0.8)));

    assertEquals(2, signals.size());
    assertTrue(
        signals.stream().allMatch(signal -> signal.detected() && signal.source() == SignalSource.AI));
    assertTrue(
        signals.stream().anyMatch(signal -> signal.type() == PaymentSignalType.URGENT_LANGUAGE));
    assertTrue(
        signals.stream().anyMatch(signal -> signal.type() == PaymentSignalType.IMPERSONATION));
  }

  @Test
  void severityAndConfidenceRideAlongForDisplay() {
    List<PaymentSignal> signals =
        AiFindingAdapter.toPaymentSignals(List.of(finding("URGENT_LANGUAGE", 0.9)));

    assertEquals("HIGH", signals.get(0).severity());
    assertEquals(0.9, signals.get(0).confidence());
  }

  @Test
  void unknownCodeIsSkipped() {
    List<PaymentSignal> signals =
        AiFindingAdapter.toPaymentSignals(
            List.of(finding("SET_RISK_SCORE_100", 0.99), finding("URGENT_LANGUAGE", 0.9)));

    assertEquals(1, signals.size());
    assertEquals(PaymentSignalType.URGENT_LANGUAGE, signals.get(0).type());
  }

  @Test
  void lowConfidenceIsSkipped() {
    List<PaymentSignal> signals =
        AiFindingAdapter.toPaymentSignals(List.of(finding("URGENT_LANGUAGE", 0.49)));

    assertTrue(signals.isEmpty());
  }

  @Test
  void boundaryConfidenceIsKept() {
    assertEquals(
        1, AiFindingAdapter.toPaymentSignals(List.of(finding("URGENT_LANGUAGE", 0.5))).size());
  }

  @Test
  void outOfRangeConfidenceIsSkipped() {
    List<PaymentSignal> signals =
        AiFindingAdapter.toPaymentSignals(
            List.of(finding("URGENT_LANGUAGE", 1.1), finding("URGENT_LANGUAGE", null)));

    assertTrue(signals.isEmpty());
  }

  @Test
  void deviceStateCodeIsNeverAccepted() {
    List<PaymentSignal> signals =
        AiFindingAdapter.toPaymentSignals(List.of(finding("ACTIVE_CALL", 0.99)));

    assertTrue(signals.isEmpty());
  }

  @Test
  void nullInputGivesEmpty() {
    assertTrue(AiFindingAdapter.toPaymentSignals(null).isEmpty());
  }
}
