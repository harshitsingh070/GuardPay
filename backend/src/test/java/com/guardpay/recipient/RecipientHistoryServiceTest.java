package com.guardpay.recipient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipientHistoryServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();

  @Mock private TransactionRepository transactions;

  private RecipientHistoryService service;
  private User user;
  private Recipient recipient;

  @BeforeEach
  void setUp() {
    service = new RecipientHistoryService(transactions);
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    recipient = new Recipient();
    ReflectionTestUtils.setField(recipient, "id", RECIPIENT_ID);
    recipient.setUser(user);
  }

  @Test
  void loadsScopedCompletedHistoryAndComputesMedian() {
    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(
            List.of(
                transaction("60", "USD", TransactionStatus.COMPLETED, 3),
                transaction("40", "USD", TransactionStatus.COMPLETED, 1),
                transaction("50", "USD", TransactionStatus.COMPLETED, 2)));

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertTrue(context.identified());
    assertEquals(3, context.completedPaymentCount());
    assertTrue(context.amountHistorySufficient());
    assertEquals(0, new BigDecimal("50.00").compareTo(context.medianCompletedAmount()));
    assertEquals(
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toLocalDate(),
        context.lastCompletedAt().toLocalDate());
    assertNull(context.reason());
  }

  @Test
  void ignoresFutureWrongCurrencyExcludedAndInvalidRows() {
    Transaction valid = transaction("50", "USD", TransactionStatus.COMPLETED, 1);
    Transaction future =
        transaction(
            "999",
            "USD",
            TransactionStatus.COMPLETED,
            -1);
    Transaction wrongCurrency = transaction("999", "EUR", TransactionStatus.COMPLETED, 1);
    Transaction invalidAmount = transaction("0", "USD", TransactionStatus.COMPLETED, 1);
    Transaction excluded = transaction("999", "USD", TransactionStatus.COMPLETED, 1);
    ReflectionTestUtils.setField(excluded, "id", UUID.randomUUID());

    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(valid, future, wrongCurrency, invalidAmount, excluded));

    RecipientRiskContext context = service.load(user, recipient, excluded.getId(), "USD");

    assertEquals(1, context.completedPaymentCount());
    assertEquals(0, new BigDecimal("50.00").compareTo(context.medianCompletedAmount()));
    assertFalse(context.amountHistorySufficient());
  }

  @Test
  void deduplicatesRepeatedTransactionRows() {
    Transaction repeated = transaction("50", "USD", TransactionStatus.COMPLETED, 1);
    ReflectionTestUtils.setField(repeated, "id", UUID.randomUUID());
    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(repeated, repeated));

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertEquals(1, context.completedPaymentCount());
  }

  @Test
  void foreignRecipientIsUnavailable() {
    User stranger = new User();
    ReflectionTestUtils.setField(stranger, "id", OTHER_USER_ID);
    recipient.setUser(stranger);

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertFalse(context.identified());
    assertEquals("RECIPIENT_IDENTITY_UNAVAILABLE", context.reason());
  }

  @Test
  void countsRecentRecipientAttemptsWithinProviderWindow() {
    when(transactions.findByUserIdAndRecipientIdAndTransactionTimestampAfter(
            eq(USER_ID), eq(RECIPIENT_ID), any(), any(Pageable.class)))
        .thenReturn(
            List.of(
                transaction("10", "USD", TransactionStatus.FAILED, 0),
                transaction("10", "USD", TransactionStatus.CANCELLED, 0)));
    when(transactions.countRecipientUnsuccessfulAttempts(
            eq(USER_ID), eq(RECIPIENT_ID), any(), any(), any()))
        .thenReturn(2L);

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertEquals(2, context.priorAttemptsInWindow());
    assertEquals(2, context.unsuccessfulAttemptCount());
    verify(transactions)
        .countRecipientUnsuccessfulAttempts(
            eq(USER_ID),
            eq(RECIPIENT_ID),
            eq(List.of(TransactionStatus.FAILED, TransactionStatus.CANCELLED)),
            any(),
            any());
  }

  @Test
  void noPreviousTransactionsYieldsEmptyButIdentifiedHistory() {
    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(List.of());

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertTrue(context.identified());
    assertFalse(context.hasCompletedHistory());
    assertEquals(0, context.completedPaymentCount());
    assertNull(context.medianCompletedAmount());
    assertNull(context.lastCompletedAt());
    assertFalse(context.amountHistorySufficient());
    assertEquals(0, context.priorAttemptsInWindow());
    assertNull(context.reason());
  }

  @Test
  void oneSuccessfulPaymentIsNotEnoughForAmountJudgment() {
    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(transaction("42", "USD", TransactionStatus.COMPLETED, 1)));

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertEquals(1, context.completedPaymentCount());
    assertEquals(0, new BigDecimal("42.00").compareTo(context.medianCompletedAmount()));
    assertFalse(context.amountHistorySufficient());
    assertTrue(context.lastCompletedAt() != null);
  }

  @Test
  void nonCompletedStatusesNeverCountAsSuccessfulHistory() {
    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(
            List.of(
                transaction("10", "USD", TransactionStatus.PENDING, 1),
                transaction("20", "USD", TransactionStatus.APPROVED, 1),
                transaction("30", "USD", TransactionStatus.FAILED, 1),
                transaction("40", "USD", TransactionStatus.CANCELLED, 1),
                transaction("50", "USD", TransactionStatus.COMPLETED, 1)));

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertEquals(1, context.completedPaymentCount());
    assertEquals(0, new BigDecimal("50.00").compareTo(context.medianCompletedAmount()));
    assertFalse(context.amountHistorySufficient());
  }

  @Test
  void missingOwnershipOrIdentityFailsSafely() {
    assertUnavailable(service.load(null, recipient, null, "USD"));
    assertUnavailable(service.load(user, null, null, "USD"));

    Recipient withoutId = new Recipient();
    withoutId.setUser(user);
    assertUnavailable(service.load(user, withoutId, null, "USD"));

    Recipient withoutOwner = new Recipient();
    ReflectionTestUtils.setField(withoutOwner, "id", RECIPIENT_ID);
    assertUnavailable(service.load(user, withoutOwner, null, "USD"));
  }

  @Test
  void recentAttemptsAreWindowedDeduplicatedAndExcluded() {
    Transaction within = transaction("10", "USD", TransactionStatus.FAILED, 0);
    ReflectionTestUtils.setField(within, "id", UUID.randomUUID());
    Transaction outOfWindow =
        transaction("10", "USD", TransactionStatus.COMPLETED, 1);
    Transaction future = transaction("10", "USD", TransactionStatus.FAILED, -1);
    Transaction wrongCurrency = transaction("10", "EUR", TransactionStatus.FAILED, 0);
    Transaction excluded = transaction("10", "USD", TransactionStatus.FAILED, 0);
    ReflectionTestUtils.setField(excluded, "id", UUID.randomUUID());

    when(transactions.findByUserIdAndRecipientIdAndTransactionTimestampAfter(
            eq(USER_ID), eq(RECIPIENT_ID), any(), any(Pageable.class)))
        .thenReturn(
            List.of(within, within, outOfWindow, future, wrongCurrency, excluded));

    RecipientRiskContext context = service.load(user, recipient, excluded.getId(), "USD");

    assertEquals(1, context.priorAttemptsInWindow());
  }

  @Test
  void medianIsRobustToHistoricalOutliers() {
    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(
            List.of(
                transaction("40", "USD", TransactionStatus.COMPLETED, 5),
                transaction("45", "USD", TransactionStatus.COMPLETED, 4),
                transaction("50", "USD", TransactionStatus.COMPLETED, 3),
                transaction("10000", "USD", TransactionStatus.COMPLETED, 2),
                transaction("20000", "USD", TransactionStatus.COMPLETED, 1)));

    RecipientRiskContext context = service.load(user, recipient, null, "USD");

    assertEquals(0, new BigDecimal("50.00").compareTo(context.medianCompletedAmount()));
  }

  @Test
  void completedHistoryQueryIsBounded() {
    when(transactions.findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), any(Pageable.class)))
        .thenReturn(List.of());

    service.load(user, recipient, null, "USD");

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(transactions)
        .findRecipientCompletedHistory(
            eq(USER_ID), eq(RECIPIENT_ID), eq("USD"), any(), any(), pageable.capture());
    assertTrue(pageable.getValue().getPageSize() <= RecipientHistoryService.MAX_AMOUNT_SAMPLES + 1);
  }

  private static void assertUnavailable(RecipientRiskContext context) {
    assertFalse(context.identified());
    assertEquals("RECIPIENT_IDENTITY_UNAVAILABLE", context.reason());
  }

  private static Transaction transaction(
      String amount, String currency, TransactionStatus status, int daysAgo) {
    Transaction transaction = new Transaction();
    transaction.setAmount(new BigDecimal(amount));
    transaction.setCurrency(currency);
    transaction.setStatus(status);
    transaction.setTransactionTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysAgo));
    return transaction;
  }
}
