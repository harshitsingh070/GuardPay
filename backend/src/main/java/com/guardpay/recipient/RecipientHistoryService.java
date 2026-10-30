package com.guardpay.recipient;

import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipientHistoryService {

  static final int MAX_AMOUNT_SAMPLES = 20;
  static final int MIN_AMOUNT_SAMPLES = 3;
  private static final int MAX_RECENT_ATTEMPTS = 50;
  private static final UUID NEVER_EXCLUDED = new UUID(0L, 0L);

  private final TransactionRepository transactions;

  public RecipientHistoryService(TransactionRepository transactions) {
    this.transactions = transactions;
  }

  @Transactional(readOnly = true)
  public RecipientRiskContext load(
      User user, Recipient recipient, UUID excludeTransactionId, String currency) {
    if (user == null
        || user.getId() == null
        || recipient == null
        || recipient.getId() == null
        || recipient.getUser() == null
        || !user.getId().equals(recipient.getUser().getId())) {
      return RecipientRiskContext.unavailable("RECIPIENT_IDENTITY_UNAVAILABLE");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID exclude = excludeTransactionId == null ? NEVER_EXCLUDED : excludeTransactionId;

    List<Transaction> completed =
        transactions.findRecipientCompletedHistory(
            user.getId(),
            recipient.getId(),
            currency,
            now,
            exclude,
            PageRequest.of(0, MAX_AMOUNT_SAMPLES + 1));
    if (completed == null) {
      completed = List.of();
    }

    List<Transaction> validCompleted =
        completed.stream()
            .filter(transaction -> !isExcluded(transaction, exclude))
            .filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
            .filter(transaction -> matchesCurrency(transaction, currency))
            .filter(RecipientHistoryService::hasValidAmount)
            .filter(transaction -> transaction.getTransactionTimestamp() != null)
            .filter(transaction -> transaction.getTransactionTimestamp().isBefore(now))
            .sorted(
                Comparator.comparing(Transaction::getTransactionTimestamp).reversed())
            .toList();

    List<Transaction> uniqueCompleted = deduplicate(validCompleted);
    boolean truncated = uniqueCompleted.size() > MAX_AMOUNT_SAMPLES;
    List<Transaction> samples =
        uniqueCompleted.subList(0, Math.min(MAX_AMOUNT_SAMPLES, uniqueCompleted.size()));

    int unsuccessful =
        (int)
            Math.min(
                Integer.MAX_VALUE,
                transactions.countRecipientUnsuccessfulAttempts(
                    user.getId(),
                    recipient.getId(),
                    List.of(TransactionStatus.FAILED, TransactionStatus.CANCELLED),
                    now,
                    exclude));

    OffsetDateTime cutoff =
        now.minusMinutes(DeterministicRiskEngine.REPEATED_ATTEMPT_WINDOW_MINUTES);
    List<Transaction> recentAttempts =
        transactions.findByUserIdAndRecipientIdAndTransactionTimestampAfter(
            user.getId(),
            recipient.getId(),
            cutoff,
            PageRequest.of(0, MAX_RECENT_ATTEMPTS));
    List<Transaction> validRecentAttempts =
        (recentAttempts == null ? List.<Transaction>of() : recentAttempts)
            .stream()
            .filter(transaction -> !isExcluded(transaction, exclude))
            .filter(transaction -> matchesCurrency(transaction, currency))
            .filter(transaction -> transaction.getTransactionTimestamp() != null)
            .filter(transaction -> !transaction.getTransactionTimestamp().isBefore(cutoff))
            .filter(transaction -> !transaction.getTransactionTimestamp().isAfter(now))
            .toList();
    long recentRecipientAttempts = deduplicate(validRecentAttempts).size();

    return new RecipientRiskContext(
        true,
        null,
        samples.size(),
        truncated,
        unsuccessful,
        samples.stream()
            .map(Transaction::getTransactionTimestamp)
            .max(Comparator.naturalOrder())
            .orElse(null),
        medianOf(samples),
        samples.size() >= MIN_AMOUNT_SAMPLES,
        (int) Math.min(Integer.MAX_VALUE, recentRecipientAttempts));
  }

  private static List<Transaction> deduplicate(List<Transaction> transactions) {
    List<Transaction> unique = new ArrayList<>();
    Set<UUID> ids = new HashSet<>();
    for (Transaction transaction : transactions) {
      if (transaction.getId() == null || ids.add(transaction.getId())) {
        unique.add(transaction);
      }
    }
    return unique;
  }

  private static boolean matchesCurrency(Transaction transaction, String currency) {
    return currency == null || currency.equalsIgnoreCase(transaction.getCurrency());
  }

  private static boolean hasValidAmount(Transaction transaction) {
    return transaction.getAmount() != null && transaction.getAmount().signum() > 0;
  }

  private static boolean isExcluded(Transaction transaction, UUID excludeId) {
    return excludeId.equals(transaction.getId());
  }

  private static BigDecimal medianOf(List<Transaction> history) {
    if (history.isEmpty()) {
      return null;
    }
    List<BigDecimal> amounts =
        history.stream()
            .map(Transaction::getAmount)
            .sorted()
            .toList();
    int middle = amounts.size() / 2;
    if (amounts.size() % 2 == 1) {
      return amounts.get(middle).setScale(2, RoundingMode.HALF_UP);
    }
    return amounts
        .get(middle - 1)
        .add(amounts.get(middle))
        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
  }
}
