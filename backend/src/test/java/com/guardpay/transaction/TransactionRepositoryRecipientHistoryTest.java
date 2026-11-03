package com.guardpay.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guardpay.recipient.Recipient;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

/** JPQL semantics for recipient attempt counts and completed-history ordering. */
@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class TransactionRepositoryRecipientHistoryTest {

  private static final OffsetDateTime EVALUATION_TIME =
      OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime WINDOW_START = EVALUATION_TIME.minusMinutes(10);
  private static final UUID NEVER_EXCLUDED = new UUID(0L, 0L);

  @Autowired private TransactionRepository transactions;
  @Autowired private TestEntityManager entities;

  @Test
  void countsOnlyOwnerRecipientCurrencyWindowAndExcludesCurrentAndFuture() {
    User owner = user("owner");
    User stranger = user("stranger");
    Recipient target = recipient(owner, "Target");
    Recipient otherRecipient = recipient(owner, "Other recipient");
    Recipient foreignRecipient = recipient(stranger, "Foreign recipient");

    transaction(owner, target, TransactionStatus.COMPLETED, "USD", "11:55");
    transaction(owner, target, TransactionStatus.FAILED, "USD", "11:50");
    transaction(owner, target, TransactionStatus.CANCELLED, "USD", "12:00");
    transaction(owner, target, TransactionStatus.APPROVED, "USD", "11:56");
    transaction(owner, target, TransactionStatus.COMPLETED, "USD", "11:49:59");
    transaction(owner, target, TransactionStatus.COMPLETED, "USD", "12:00:01");
    transaction(stranger, foreignRecipient, TransactionStatus.COMPLETED, "USD", "11:55");
    transaction(owner, otherRecipient, TransactionStatus.COMPLETED, "USD", "11:55");
    transaction(owner, target, TransactionStatus.COMPLETED, "EUR", "11:55");
    Transaction current = transaction(owner, target, TransactionStatus.PENDING, "USD", "11:58");

    entities.flush();

    long count =
        transactions.countRecipientAttemptsInWindow(
            owner.getId(), target.getId(), "USD", WINDOW_START, EVALUATION_TIME, current.getId());

    assertEquals(4, count);
  }

  @Test
  void countsMoreThanFiftyAttemptsWithoutAPageCap() {
    User owner = user("owner-volume");
    Recipient target = recipient(owner, "Target");

    for (int i = 0; i < 60; i++) {
      OffsetDateTime timestamp = WINDOW_START.plusSeconds(1 + i);
      transaction(owner, target, TransactionStatus.FAILED, "USD", timestamp);
    }
    entities.flush();

    long count =
        transactions.countRecipientAttemptsInWindow(
            owner.getId(), target.getId(), "USD", WINDOW_START, EVALUATION_TIME, NEVER_EXCLUDED);

    assertEquals(60, count);
  }

  @Test
  void completedHistoryIsOrderedByTimestampDescendingRegardlessOfInsertionOrder() {
    User owner = user("owner-order");
    Recipient target = recipient(owner, "Target");
    transaction(owner, target, TransactionStatus.COMPLETED, "USD", "11:55");
    transaction(owner, target, TransactionStatus.COMPLETED, "USD", "11:40");
    transaction(owner, target, TransactionStatus.COMPLETED, "USD", "11:58");
    entities.flush();

    List<Transaction> history =
        transactions.findRecipientCompletedHistory(
            owner.getId(),
            target.getId(),
            "USD",
            EVALUATION_TIME,
            NEVER_EXCLUDED,
            PageRequest.of(0, 21));

    assertEquals(
        List.of("11:58", "11:55", "11:40"),
        history.stream().map(TransactionRepositoryRecipientHistoryTest::time).toList());
  }

  private User user(String externalId) {
    User user = new User();
    user.setExternalUserId(externalId);
    user.setEmail(externalId + "@example.com");
    user.setCurrency("USD");
    entities.persist(user);
    return user;
  }

  private Recipient recipient(User owner, String name) {
    Recipient recipient = new Recipient();
    recipient.setUser(owner);
    recipient.setName(name);
    entities.persist(recipient);
    return recipient;
  }

  private Transaction transaction(
      User owner,
      Recipient recipient,
      TransactionStatus status,
      String currency,
      String time) {
    return transaction(
        owner, recipient, status, currency, OffsetDateTime.parse("2026-01-01T" + time + "Z"));
  }

  private Transaction transaction(
      User owner,
      Recipient recipient,
      TransactionStatus status,
      String currency,
      OffsetDateTime timestamp) {
    Transaction transaction = new Transaction();
    transaction.setUser(owner);
    transaction.setRecipient(recipient);
    transaction.setAmount(new BigDecimal("25.00"));
    transaction.setCurrency(currency);
    transaction.setTransactionType(TransactionType.PAYMENT);
    transaction.setStatus(status);
    transaction.setTransactionTimestamp(timestamp);
    entities.persist(transaction);
    return transaction;
  }

  private static String time(Transaction transaction) {
    return transaction.getTransactionTimestamp().toLocalTime().toString();
  }
}
