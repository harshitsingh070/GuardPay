package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.RiskEvaluationRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Adapter maps evidence onto the engine contract without scoring anything. */
class AutomaticSignalAdapterTest {

  private static AutomaticSignalCollectionResult collection(
      boolean large, boolean fresh, boolean repeated) {
    return new AutomaticSignalCollectionResult(
        List.of(
            new PaymentSignal(
                PaymentSignalType.UNUSUAL_AMOUNT, large, SignalSource.TRANSACTION_HISTORY, null, "e"),
            new PaymentSignal(
                PaymentSignalType.REPEATED_ATTEMPTS,
                repeated,
                SignalSource.TRANSACTION_HISTORY,
                null,
                "e"),
            new PaymentSignal(
                PaymentSignalType.NEW_RECIPIENT, fresh, SignalSource.TRANSACTION_HISTORY, null, "e"),
            new PaymentSignal(
                PaymentSignalType.UNKNOWN_MERCHANT, false, SignalSource.MERCHANT_HISTORY, null, "e"),
            new PaymentSignal(
                PaymentSignalType.UNEXPECTED_INVOICE,
                false,
                SignalSource.PAYMENT_CONTEXT,
                "INSUFFICIENT_CONTEXT",
                "e"),
            new PaymentSignal(
                PaymentSignalType.SUSPICIOUS_URL,
                false,
                SignalSource.URL_REPUTATION,
                "URL_REPUTATION_NOT_CONFIGURED",
                "e"),
            new PaymentSignal(
                PaymentSignalType.ACTIVE_CALL, false, SignalSource.NOT_AVAILABLE, "NOT_AVAILABLE_YET", "e"),
            new PaymentSignal(
                PaymentSignalType.URGENT_LANGUAGE,
                false,
                SignalSource.NOT_AVAILABLE,
                "NOT_AVAILABLE_YET",
                "e"),
            new PaymentSignal(
                PaymentSignalType.SOCIAL_ENGINEERING_DETECTED,
                false,
                SignalSource.NOT_AVAILABLE,
                "NOT_AVAILABLE_YET",
                "e")),
        new BigDecimal("87"));
  }

  @Test
  void detectedSignalsMapToEngineFlags() {
    RiskEvaluationRequest request =
        AutomaticSignalAdapter.toRiskEvaluationRequest(
            new BigDecimal("2499"), collection(true, true, true));

    assertEquals(0, new BigDecimal("2499").compareTo(request.currentAmount()));
    assertEquals(0, new BigDecimal("87").compareTo(request.historicalAverage()));
    assertTrue(request.newRecipient());
    assertTrue(request.repeatedAttempts());
    assertFalse(request.unknownMerchant());
    assertFalse(request.unexpectedInvoice());
    assertFalse(request.suspiciousUrl());
    assertFalse(request.activeCall());
    assertFalse(request.urgentLanguage());
    assertFalse(request.socialEngineeringDetected());
  }

  @Test
  void undetectedRepeatedAttemptsStaysFalse() {
    RiskEvaluationRequest request =
        AutomaticSignalAdapter.toRiskEvaluationRequest(
            new BigDecimal("45"), collection(false, false, false));

    assertFalse(request.repeatedAttempts());
  }
}
