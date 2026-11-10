package com.guardpay.behavior.highvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.recipient.Recipient;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FirstTimeHighValueDetectionEngineTest {

  private FirstTimeHighValueDetectionEngine engine;
  private FirstTimeHighValueConfig config;
  private User testUser;
  private Recipient testRecipient1;
  private Recipient testRecipient2;
  private OffsetDateTime now;

  @BeforeEach
  void setUp() {
    config = FirstTimeHighValueConfig.defaults();
    engine = new FirstTimeHighValueDetectionEngine(config);

    now = OffsetDateTime.now(ZoneOffset.UTC);

    testUser = new User();
    ReflectionTestUtils.setField(testUser, "id", UUID.randomUUID());

    testRecipient1 = new Recipient();
    ReflectionTestUtils.setField(testRecipient1, "id", UUID.randomUUID());
    testRecipient1.setUser(testUser);
    testRecipient1.setName("Established payee");

    testRecipient2 = new Recipient();
    ReflectionTestUtils.setField(testRecipient2, "id", UUID.randomUUID());
    testRecipient2.setUser(testUser);
    testRecipient2.setName("New payee");
  }

  private Transaction createTx(
      BigDecimal amount,
      String currency,
      Recipient recipient,
      TransactionStatus status,
      OffsetDateTime timestamp) {
    Transaction t = new Transaction();
    ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
    t.setUser(testUser);
    t.setAmount(amount);
    t.setCurrency(currency);
    t.setRecipient(recipient);
    t.setStatus(status);
    t.setTransactionTimestamp(timestamp);
    return t;
  }

  @Test
  void invalidOrNegativeAmountReturnsColdStart() {
    FirstTimeHighValueAssessment res1 =
        engine.evaluate(testUser.getId(), null, "USD", null, List.of(), null, now, "ESTABLISHED");
    assertFalse(res1.detected());
    assertEquals(FirstTimeHighValueClassification.COLD_START_INSUFFICIENT_DATA, res1.classification());

    FirstTimeHighValueAssessment res2 =
        engine.evaluate(
            testUser.getId(), new BigDecimal("-50.00"), "USD", null, List.of(), null, now, "ESTABLISHED");
    assertFalse(res2.detected());
  }

  @Test
  void insufficientHistoryReturnsColdStartWithoutFraud() {
    // 3 transactions < 5 threshold
    List<Transaction> history =
        List.of(
            createTx(new BigDecimal("100"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(10)),
            createTx(new BigDecimal("120"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(5)),
            createTx(new BigDecimal("110"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(2)));

    FirstTimeHighValueAssessment res =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("5000"),
            "USD",
            testRecipient2.getId(),
            history,
            null,
            now,
            "INSUFFICIENT_DATA");

    assertFalse(res.detected(), "Cold-start user must never be flagged as fraud");
    assertEquals(FirstTimeHighValueClassification.COLD_START_INSUFFICIENT_DATA, res.classification());
    assertNull(res.historicalMedian(), "Baseline must not be fabricated for cold start");
    assertTrue(engine.toSignals(res).isEmpty());
  }

  @Test
  void currencySeparationNeverMixesCurrencies() {
    // History has 5 USD transactions, but request is in EUR
    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      history.add(
          createTx(
              new BigDecimal("100"),
              "USD",
              testRecipient1,
              TransactionStatus.COMPLETED,
              now.minusDays(20 - i)));
    }

    FirstTimeHighValueAssessment res =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("5000"),
            "EUR", // Request in EUR
            testRecipient2.getId(),
            history,
            null,
            now,
            "ESTABLISHED");

    assertFalse(res.detected(), "EUR has 0 transactions so it must be treated as cold start");
    assertEquals(FirstTimeHighValueClassification.COLD_START_INSUFFICIENT_DATA, res.classification());
  }

  @Test
  void firstPaymentToNewRecipientHighValueDetected() {
    // Scenario from master prompt: User typically pays 500-2,000, max 7,000, 90th percentile 4,000
    // Suddenly initiates 75,000 payment to a new recipient
    List<Transaction> history = new ArrayList<>();
    int[] pastAmounts = {500, 700, 800, 1000, 1200, 1500, 1800, 2000, 2500, 3000, 4000, 7000};
    for (int i = 0; i < pastAmounts.length; i++) {
      history.add(
          createTx(
              BigDecimal.valueOf(pastAmounts[i]),
              "INR",
              testRecipient1, // All sent to recipient 1
              TransactionStatus.COMPLETED,
              now.minusDays(60 - i * 4)));
    }

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("75000"),
            "INR",
            testRecipient2.getId(), // New recipient
            history,
            null,
            now,
            "ESTABLISHED");

    assertTrue(assessment.detected());
    assertEquals(
        FirstTimeHighValueClassification.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE,
        assessment.classification());
    assertEquals("CRITICAL", assessment.severity());
    assertTrue(assessment.isNewRecipient());
    assertEquals(0, assessment.recipientPaymentCount());
    assertTrue(assessment.amountRatio().compareTo(new BigDecimal("10")) > 0);
    assertTrue(assessment.ratioOverHistoricalMax().compareTo(new BigDecimal("10")) > 0);
    assertNotNull(assessment.historicalMedian());
    assertNotNull(assessment.historicalMax());
    assertEquals(new BigDecimal("7000"), assessment.historicalMax());
    assertNotNull(assessment.evidence());
    assertTrue(assessment.evidence().contains("substantially larger than your usual payments"));

    List<PaymentSignal> signals = engine.toSignals(assessment);
    assertEquals(1, signals.size());
    assertEquals(
        PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE, signals.get(0).type());
    assertTrue(signals.get(0).detected());
  }

  @Test
  void legitimateHighValueUserNotFlagged() {
    // User with established history of 50,000 - 100,000 payments paying 75,000
    List<Transaction> history = new ArrayList<>();
    int[] pastAmounts = {50000, 60000, 70000, 75000, 80000, 90000, 100000};
    for (int i = 0; i < pastAmounts.length; i++) {
      history.add(
          createTx(
              BigDecimal.valueOf(pastAmounts[i]),
              "INR",
              testRecipient1,
              TransactionStatus.COMPLETED,
              now.minusDays(50 - i * 5)));
    }

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("75000"),
            "INR",
            testRecipient1.getId(),
            history,
            null,
            now,
            "ESTABLISHED");

    assertFalse(assessment.detected(), "Legitimate high-value user should not be flagged");
    assertEquals(FirstTimeHighValueClassification.NORMAL_PAYMENT, assessment.classification());
    assertTrue(engine.toSignals(assessment).isEmpty());
  }

  @Test
  void normalPaymentToNewRecipientNotFlaggedAsHighValue() {
    // User pays 100-200, sends 120 to new recipient
    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(
          createTx(
              new BigDecimal("100"),
              "USD",
              testRecipient1,
              TransactionStatus.COMPLETED,
              now.minusDays(30 - i * 2)));
    }

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("120"),
            "USD",
            testRecipient2.getId(), // New recipient
            history,
            null,
            now,
            "ESTABLISHED");

    assertFalse(
        assessment.detected(),
        "Ordinary amount to new recipient must not be flagged as high-value anomaly");
    assertEquals(FirstTimeHighValueClassification.NORMAL_PAYMENT, assessment.classification());
  }

  @Test
  void firstHighValueToExistingRecipientDetected() {
    // User previously paid recipient 1 three times: $50, $60, $70
    // General user history has payments up to $1000
    // Now sends $600 to recipient 1 (which is 10x recipient median and 8x recipient max)
    List<Transaction> history = new ArrayList<>();
    // Recipient 1 payments
    history.add(createTx(new BigDecimal("50"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(40)));
    history.add(createTx(new BigDecimal("60"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(30)));
    history.add(createTx(new BigDecimal("70"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(20)));
    // Other payments
    history.add(createTx(new BigDecimal("200"), "USD", testRecipient2, TransactionStatus.COMPLETED, now.minusDays(15)));
    history.add(createTx(new BigDecimal("300"), "USD", testRecipient2, TransactionStatus.COMPLETED, now.minusDays(10)));
    history.add(createTx(new BigDecimal("500"), "USD", testRecipient2, TransactionStatus.COMPLETED, now.minusDays(5)));

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("600"),
            "USD",
            testRecipient1.getId(), // Known recipient
            history,
            null,
            now,
            "ESTABLISHED");

    assertTrue(assessment.detected());
    assertEquals(
        FirstTimeHighValueClassification.FIRST_HIGH_VALUE_TO_RECIPIENT,
        assessment.classification());
    assertFalse(assessment.isNewRecipient());
    assertEquals(3, assessment.recipientPaymentCount());
    assertEquals(new BigDecimal("60.00"), assessment.recipientMedian());
    assertEquals(new BigDecimal("70"), assessment.recipientMax());
    assertTrue(assessment.evidence().contains("previous maximum payment"));

    List<PaymentSignal> signals = engine.toSignals(assessment);
    assertEquals(1, signals.size());
    assertEquals(
        PaymentSignalType.FIRST_HIGH_VALUE_TO_RECIPIENT, signals.get(0).type());
  }

  @Test
  void zeroMadDistributionHandledSafelyWithFallback() {
    // User paid exactly 100 every single time (MAD == 0)
    // Suddenly pays 1,000 (10x median)
    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(
          createTx(
              new BigDecimal("100"),
              "USD",
              testRecipient1,
              TransactionStatus.COMPLETED,
              now.minusDays(30 - i * 2)));
    }

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("1000"),
            "USD",
            testRecipient1.getId(),
            history,
            null,
            now,
            "ESTABLISHED");

    assertTrue(assessment.detected(), "Zero-MAD distribution must detect significant ratio increase");
    assertNull(assessment.modifiedZScore(), "Zero-MAD cannot divide by zero");
    assertEquals(new BigDecimal("10.0000"), assessment.amountRatio());
    assertTrue(
        assessment.classification() == FirstTimeHighValueClassification.FIRST_HIGH_VALUE_PAYMENT
            || assessment.classification() == FirstTimeHighValueClassification.FIRST_HIGH_VALUE_TO_RECIPIENT
            || assessment.classification() == FirstTimeHighValueClassification.SIGNIFICANT_AMOUNT_DEVIATION);
  }

  @Test
  void rapidAmountEscalationDetected() {
    // Baseline over 90 days has payments around $100.
    // In the last 7 days, payments were $50 and $60.
    // Suddenly payment jumps to $800.
    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(
          createTx(
              new BigDecimal("100"),
              "USD",
              testRecipient1,
              TransactionStatus.COMPLETED,
              now.minusDays(80 - i * 5)));
    }
    history.add(createTx(new BigDecimal("50"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(4)));
    history.add(createTx(new BigDecimal("60"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(2)));

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("800"),
            "USD",
            testRecipient1.getId(),
            history,
            null,
            now,
            "ESTABLISHED");

    assertTrue(assessment.detected());
    assertNotNull(assessment.classification());
  }

  @Test
  void inFlightTransactionIsExcludedFromItsOwnBaseline() {
    UUID inFlightId = UUID.randomUUID();
    Transaction inFlightTx =
        createTx(new BigDecimal("75000"), "INR", testRecipient1, TransactionStatus.COMPLETED, now);
    ReflectionTestUtils.setField(inFlightTx, "id", inFlightId);

    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(
          createTx(
              new BigDecimal("1000"),
              "INR",
              testRecipient1,
              TransactionStatus.COMPLETED,
              now.minusDays(30 - i * 2)));
    }
    // Accidentally included in-flight transaction in the list
    history.add(inFlightTx);

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("75000"),
            "INR",
            testRecipient2.getId(),
            history,
            inFlightId, // Excluded!
            now,
            "ESTABLISHED");

    assertTrue(assessment.detected());
    assertEquals(new BigDecimal("1000"), assessment.historicalMax(), "In-flight 75000 must NOT be in historical max");
  }

  @Test
  void stoppedAndFailedTransactionsExcludedFromBaseline() {
    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      history.add(createTx(new BigDecimal("100"), "USD", testRecipient1, TransactionStatus.COMPLETED, now.minusDays(20 - i)));
    }
    // Failed and Stopped attempts of large amounts
    history.add(createTx(new BigDecimal("100000"), "USD", testRecipient2, TransactionStatus.CANCELLED, now.minusDays(3)));
    history.add(createTx(new BigDecimal("50000"), "USD", testRecipient2, TransactionStatus.FAILED, now.minusDays(2)));

    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            testUser.getId(),
            new BigDecimal("2000"),
            "USD",
            testRecipient2.getId(),
            history,
            null,
            now,
            "ESTABLISHED");

    assertTrue(assessment.detected());
    assertEquals(new BigDecimal("100"), assessment.historicalMax(), "STOPPED and FAILED transactions must be excluded");
  }
}

