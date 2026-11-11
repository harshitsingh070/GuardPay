package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SignalOverlapResolverTest {

  private static PaymentSignal detected(PaymentSignalType type) {
    return new PaymentSignal(type, true, SignalSource.TRANSACTION_HISTORY, null, "evidence");
  }

  private static PaymentSignal quiet(PaymentSignalType type) {
    return new PaymentSignal(type, false, SignalSource.TRANSACTION_HISTORY, null, "evidence");
  }

  private static Map<PaymentSignalType, PaymentSignal> byType(List<PaymentSignal> signals) {
    return signals.stream()
        .collect(Collectors.toMap(PaymentSignal::type, signal -> signal));
  }

  @Test
  void keepsOnlyTheStrongestAmountExplanation() {
    List<PaymentSignal> resolved =
        SignalOverlapResolver.resolve(
            List.of(
                detected(PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE),
                detected(PaymentSignalType.SIGNIFICANT_AMOUNT_DEVIATION),
                detected(PaymentSignalType.AMOUNT_DEVIATION),
                detected(PaymentSignalType.AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM)));

    Map<PaymentSignalType, PaymentSignal> result = byType(resolved);
    assertTrue(result.get(PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE).detected());
    assertFalse(result.get(PaymentSignalType.SIGNIFICANT_AMOUNT_DEVIATION).detected());
    assertFalse(result.get(PaymentSignalType.AMOUNT_DEVIATION).detected());
    assertFalse(result.get(PaymentSignalType.AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM).detected());
    assertEquals(
        SignalOverlapResolver.SUPPRESSED_REASON,
        result.get(PaymentSignalType.AMOUNT_DEVIATION).reason());
  }

  @Test
  void aFirstPaymentAlreadyExplainsNewRecipientAndRecipientDeviation() {
    List<PaymentSignal> resolved =
        SignalOverlapResolver.resolve(
            List.of(
                detected(PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE),
                detected(PaymentSignalType.NEW_RECIPIENT),
                detected(PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION)));

    Map<PaymentSignalType, PaymentSignal> result = byType(resolved);
    assertTrue(result.get(PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE).detected());
    assertFalse(result.get(PaymentSignalType.NEW_RECIPIENT).detected());
    assertFalse(result.get(PaymentSignalType.RECIPIENT_BEHAVIOR_DEVIATION).detected());
  }

  @Test
  void velocityVariantsCountAsOneBurst() {
    List<PaymentSignal> resolved =
        SignalOverlapResolver.resolve(
            List.of(
                detected(PaymentSignalType.VELOCITY_DAILY),
                detected(PaymentSignalType.VELOCITY_HOURLY),
                detected(PaymentSignalType.REPEATED_ATTEMPTS)));

    Map<PaymentSignalType, PaymentSignal> result = byType(resolved);
    assertTrue(result.get(PaymentSignalType.VELOCITY_DAILY).detected());
    assertFalse(result.get(PaymentSignalType.VELOCITY_HOURLY).detected());
    assertFalse(result.get(PaymentSignalType.REPEATED_ATTEMPTS).detected());
  }

  @Test
  void overallDeviationIsDroppedWhenASpecificDeviationExplainsIt() {
    List<PaymentSignal> resolved =
        SignalOverlapResolver.resolve(
            List.of(
                detected(PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION),
                detected(PaymentSignalType.AMOUNT_DEVIATION)));

    Map<PaymentSignalType, PaymentSignal> result = byType(resolved);
    assertTrue(result.get(PaymentSignalType.AMOUNT_DEVIATION).detected());
    assertFalse(result.get(PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION).detected());
  }

  @Test
  void unrelatedSignalsAndQuietSignalsAreUntouched() {
    PaymentSignal unknownMerchant = detected(PaymentSignalType.UNKNOWN_MERCHANT);
    PaymentSignal quiet = quiet(PaymentSignalType.AMOUNT_DEVIATION);
    List<PaymentSignal> resolved = SignalOverlapResolver.resolve(List.of(unknownMerchant, quiet));

    Map<PaymentSignalType, PaymentSignal> result = byType(resolved);
    assertTrue(result.get(PaymentSignalType.UNKNOWN_MERCHANT).detected());
    assertFalse(result.get(PaymentSignalType.AMOUNT_DEVIATION).detected());
    assertEquals(
        SignalSource.TRANSACTION_HISTORY,
        result.get(PaymentSignalType.UNKNOWN_MERCHANT).source());
  }
}
