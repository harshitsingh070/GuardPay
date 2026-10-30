package com.guardpay.recipient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RecipientRiskEvaluatorTest {

  private final RecipientRiskEvaluator evaluator = new RecipientRiskEvaluator();

  @Test
  void amountAnomalyFiresAtFiveTimesRecipientMedian() {
    RecipientRiskContext context = context(3, "50.00", true);

    PaymentSignal signal =
        evaluator.amountAnomaly(new BigDecimal("250"), context, false);

    assertTrue(signal.detected());
    assertEquals(PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY, signal.type());
  }

  @Test
  void amountAnomalyDoesNotFireJustBelowBoundary() {
    RecipientRiskContext context = context(3, "50.00", true);

    PaymentSignal signal =
        evaluator.amountAnomaly(new BigDecimal("249.99"), context, false);

    assertFalse(signal.detected());
  }

  @Test
  void accountWideAmountSignalPreventsDoubleCounting() {
    RecipientRiskContext context = context(3, "50.00", true);

    PaymentSignal signal =
        evaluator.amountAnomaly(new BigDecimal("999"), context, true);

    assertFalse(signal.detected());
    assertEquals("ACCOUNT_WIDE_AMOUNT_FLAGGED", signal.reason());
  }

  @Test
  void insufficientRecipientHistorySkipsAmountJudgment() {
    RecipientRiskContext context = context(2, "50.00", false);

    PaymentSignal signal =
        evaluator.amountAnomaly(new BigDecimal("999"), context, false);

    assertFalse(signal.detected());
    assertEquals("INSUFFICIENT_RECIPIENT_HISTORY", signal.reason());
  }

  @Test
  void missingAmountIsUnavailableNotAnomalous() {
    PaymentSignal signal = evaluator.amountAnomaly(null, context(3, "50.00", true), false);

    assertFalse(signal.detected());
    assertEquals("AMOUNT_UNAVAILABLE", signal.reason());
  }

  @Test
  void zeroAmountIsHandledSafely() {
    PaymentSignal signal =
        evaluator.amountAnomaly(BigDecimal.ZERO, context(3, "50.00", true), false);

    assertFalse(signal.detected());
    assertNull(signal.reason());
  }

  @Test
  void unidentifiedRecipientNeverProducesAnomalyOrNewSignal() {
    RecipientRiskContext unavailable = RecipientRiskContext.unavailable("RECIPIENT_IDENTITY_UNAVAILABLE");

    PaymentSignal amount = evaluator.amountAnomaly(new BigDecimal("999"), unavailable, false);
    PaymentSignal fresh = evaluator.newRecipient(unavailable);

    assertFalse(amount.detected());
    assertEquals("RECIPIENT_IDENTITY_UNAVAILABLE", amount.reason());
    assertFalse(fresh.detected());
    assertEquals("RECIPIENT_IDENTITY_UNAVAILABLE", fresh.reason());
  }

  @Test
  void repeatedAttemptsBelowThresholdDoesNotFire() {
    PaymentSignal signal = evaluator.repeatedAttempts(0, context(3, "50.00", true));

    assertFalse(signal.detected());
    assertEquals(PaymentSignalType.REPEATED_ATTEMPTS, signal.type());
  }

  @Test
  void repeatedAttemptsWithoutRecipientIdentityDoNotClaimRecipientEvidence() {
    PaymentSignal signal =
        evaluator.repeatedAttempts(
            0, RecipientRiskContext.unavailable("RECIPIENT_IDENTITY_UNAVAILABLE"));

    assertFalse(signal.detected());
    assertFalse(signal.evidence().contains("this recipient"));
  }

  @Test
  void newRecipientUsesCompletedHistoryOnly() {
    PaymentSignal fresh = evaluator.newRecipient(context(0, null, false));
    PaymentSignal known = evaluator.newRecipient(context(1, "50.00", false));

    assertTrue(fresh.detected());
    assertFalse(known.detected());
  }

  @Test
  void repeatedAttemptsPreferRecipientSpecificEvidence() {
    RecipientRiskContext context =
        new RecipientRiskContext(
            true,
            null,
            3,
            false,
            0,
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
            new BigDecimal("50.00"),
            true,
            2);

    PaymentSignal signal = evaluator.repeatedAttempts(2, context);

    assertTrue(signal.detected());
    assertTrue(signal.evidence().contains("to this recipient"));
  }

  private static RecipientRiskContext context(
      int completedCount, String median, boolean sufficient) {
    return new RecipientRiskContext(
        true,
        null,
        completedCount,
        false,
        0,
        completedCount == 0 ? null : OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
        median == null ? null : new BigDecimal(median),
        sufficient,
        0);
  }
}
