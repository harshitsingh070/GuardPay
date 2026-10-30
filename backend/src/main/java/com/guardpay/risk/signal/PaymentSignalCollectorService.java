package com.guardpay.risk.signal;

import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientHistoryService;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.recipient.RecipientRiskContext;
import com.guardpay.recipient.RecipientRiskEvaluator;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import com.guardpay.risk.DeterministicRiskEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives payment signals from trusted backend data only.
 *
 * <p>History means COMPLETED transactions (failed/stopped payments never
 * count), excluding an in-flight transaction when identified, and matching
 * currency when the request names one. Anything not reliably derivable
 * reports {@code detected=false} with a reason code instead of guessing.
 * Collects evidence only — scoring stays in {@link DeterministicRiskEngine}.
 */
@Service
public class PaymentSignalCollectorService {

  /** High-value payments need at least one independent context signal. */
  static final BigDecimal HIGH_VALUE_CONTEXT_THRESHOLD = new BigDecimal("1000.00");

  private final UserRepository users;
  private final RecipientRepository recipients;
  private final MerchantRepository merchants;
  private final TransactionRepository transactions;
  private final DeterministicContentAnalyzer contentAnalyzer;
  private final RecipientHistoryService recipientHistory;
  private final RecipientRiskEvaluator recipientRiskEvaluator;

  public PaymentSignalCollectorService(
      UserRepository users,
      RecipientRepository recipients,
      MerchantRepository merchants,
      TransactionRepository transactions) {
    this(
        users,
        recipients,
        merchants,
        transactions,
        new DeterministicContentAnalyzer(),
        new RecipientHistoryService(transactions),
        new RecipientRiskEvaluator());
  }

  public PaymentSignalCollectorService(
      UserRepository users,
      RecipientRepository recipients,
      MerchantRepository merchants,
      TransactionRepository transactions,
      DeterministicContentAnalyzer contentAnalyzer) {
    this(
        users,
        recipients,
        merchants,
        transactions,
        contentAnalyzer,
        new RecipientHistoryService(transactions),
        new RecipientRiskEvaluator());
  }

  @Autowired
  public PaymentSignalCollectorService(
      UserRepository users,
      RecipientRepository recipients,
      MerchantRepository merchants,
      TransactionRepository transactions,
      DeterministicContentAnalyzer contentAnalyzer,
      RecipientHistoryService recipientHistory,
      RecipientRiskEvaluator recipientRiskEvaluator) {
    this.users = users;
    this.recipients = recipients;
    this.merchants = merchants;
    this.transactions = transactions;
    this.contentAnalyzer = contentAnalyzer;
    this.recipientHistory = recipientHistory;
    this.recipientRiskEvaluator = recipientRiskEvaluator;
  }

  @Transactional(readOnly = true)
  public AutomaticSignalCollectionResult collect(SignalCollectionRequest request) {
    // Legacy path (pre-device): device signal unavailable by definition.
    return collect(request, new DeviceCallSignal(null, "NOT_AVAILABLE_YET"));
  }

  @Transactional(readOnly = true)
  public AutomaticSignalCollectionResult collect(
      SignalCollectionRequest request, DeviceCallSignal deviceSignal) {
    User user = request.userId() == null ? null : users.findById(request.userId()).orElse(null);

    List<Transaction> history = historyFor(user, request);
    BigDecimal average = averageOf(history);

    Optional<Recipient> recipient = identifiedRecipient(request, user);
    RecipientRiskContext recipientContext =
        recipient
            .map(
                identified ->
                    recipientHistory.load(
                        user,
                        identified,
                        request.excludeTransactionId(),
                        request.currency()))
            .orElseGet(
                () -> RecipientRiskContext.unavailable("RECIPIENT_IDENTITY_UNAVAILABLE"));

    DeterministicContentAnalyzer.ContentAnalysis content =
        contentAnalyzer.analyze(request.contentText(), request.contentUrl());

    List<PaymentSignal> signals = new ArrayList<>();
    PaymentSignal unusualAmountSignal = unusualAmount(request.amount(), average);
    signals.add(unusualAmountSignal);
    signals.add(
        recipientRiskEvaluator.amountAnomaly(
            request.amount(), recipientContext, unusualAmountSignal.detected()));
    signals.add(repeatedAttempts(request, user, recipientContext));
    PaymentSignal newRecipientSignal = recipientRiskEvaluator.newRecipient(recipientContext);
    signals.add(newRecipientSignal);
    PaymentSignal unknownMerchantSignal = unknownMerchant(request, user);
    signals.add(unknownMerchantSignal);
    signals.add(unexpectedInvoice(content, request));
    signals.add(suspiciousUrl(content, request));
    PaymentSignal callSignal = activeCallSignal(deviceSignal);
    signals.add(callSignal);
    signals.add(urgentLanguage(content, request));
    signals.add(
        socialEngineering(
            content, newRecipientSignal, unknownMerchantSignal, callSignal, request));
    signals.add(missingContext(request, deviceSignal));
    return new AutomaticSignalCollectionResult(List.copyOf(signals), average);
  }

  private List<Transaction> historyFor(User user, SignalCollectionRequest request) {
    if (user == null) {
      return List.of();
    }
    return transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED).stream()
        .filter(transaction -> matchesCurrency(transaction, request.currency()))
        .filter(transaction -> hasValidAmount(transaction))
        .filter(transaction -> !isExcluded(transaction, request.excludeTransactionId()))
        .toList();
  }

  private static boolean matchesCurrency(Transaction transaction, String currency) {
    return currency == null || currency.equalsIgnoreCase(transaction.getCurrency());
  }

  private static boolean hasValidAmount(Transaction transaction) {
    return transaction.getAmount() != null && transaction.getAmount().signum() > 0;
  }

  private static boolean isExcluded(Transaction transaction, java.util.UUID excludeId) {
    return excludeId != null && excludeId.equals(transaction.getId());
  }

  private static BigDecimal averageOf(List<Transaction> history) {
    if (history.isEmpty()) {
      return null;
    }
    BigDecimal total =
        history.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal average = total.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
    return average.signum() > 0 ? average : null;
  }

  private PaymentSignal unusualAmount(BigDecimal current, BigDecimal average) {
    if (current == null || average == null) {
      return new PaymentSignal(PaymentSignalType.UNUSUAL_AMOUNT, false,
          SignalSource.TRANSACTION_HISTORY, "INSUFFICIENT_HISTORY",
          "Not enough completed payment history to judge this amount.");
    }
    // Same business definition as the engine (shared constant, not a copy).
    boolean hit =
        current.compareTo(
                average.multiply(BigDecimal.valueOf(DeterministicRiskEngine.LARGE_AMOUNT_MULTIPLIER)))
            >= 0;
    return new PaymentSignal(PaymentSignalType.UNUSUAL_AMOUNT, hit,
        SignalSource.TRANSACTION_HISTORY, null,
        hit
            ? current.toPlainString() + " is at least "
                + DeterministicRiskEngine.LARGE_AMOUNT_MULTIPLIER
                + "x the historical average of "
                + average.toPlainString() + "."
            : current.toPlainString() + " is within the normal range (average "
                + average.toPlainString() + ").");
  }

  /**
   * Velocity: counts every prior attempt (any status) in the window and adds
   * the current attempt. Missing user context therefore reports unavailable,
   * never a false "safe" or a false "fraudulent".
   */
  private PaymentSignal repeatedAttempts(
      SignalCollectionRequest request, User user, RecipientRiskContext recipientContext) {
    int windowMinutes = DeterministicRiskEngine.REPEATED_ATTEMPT_WINDOW_MINUTES;
    if (user == null) {
      return new PaymentSignal(PaymentSignalType.REPEATED_ATTEMPTS, false,
          SignalSource.TRANSACTION_HISTORY, "USER_UNAVAILABLE",
          "No user context is available for a velocity check.");
    }
    OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(windowMinutes);
    List<Transaction> recent =
        transactions.findByUserAndTransactionTimestampAfter(user, cutoff);
    long prior =
        (recent == null ? List.<Transaction>of() : recent).stream()
            .filter(transaction -> matchesCurrency(transaction, request.currency()))
            .filter(transaction -> !isExcluded(transaction, request.excludeTransactionId()))
            .count();
    return recipientRiskEvaluator.repeatedAttempts(
        (int) Math.min(Integer.MAX_VALUE, prior), recipientContext);
  }

  private Optional<Recipient> identifiedRecipient(SignalCollectionRequest request, User user) {
    if (user == null || request.recipientId() == null) {
      return Optional.empty();
    }
    return recipients
        .findById(request.recipientId())
        .filter(recipient -> recipient.getUser() != null
            && user.getId() != null
            && user.getId().equals(recipient.getUser().getId()));
  }

  private PaymentSignal unknownMerchant(SignalCollectionRequest request, User user) {
    Optional<Merchant> merchant = identifiedMerchant(request, user);
    if (merchant.isEmpty()) {
      return new PaymentSignal(PaymentSignalType.UNKNOWN_MERCHANT, false,
          SignalSource.MERCHANT_HISTORY, "MERCHANT_IDENTITY_UNAVAILABLE",
          "No reliable merchant identity was provided.");
    }
    long prior =
        transactions
            .findByUserAndMerchantAndStatus(user, merchant.get(), TransactionStatus.COMPLETED)
            .stream()
            .filter(transaction -> !isExcluded(transaction, request.excludeTransactionId()))
            .count();
    return new PaymentSignal(PaymentSignalType.UNKNOWN_MERCHANT, prior == 0,
        SignalSource.MERCHANT_HISTORY, null,
        prior == 0
            ? "This merchant has no previous completed transaction history."
            : "Merchant is known from previous transactions.");
  }

  private Optional<Merchant> identifiedMerchant(SignalCollectionRequest request, User user) {
    if (user == null || request.merchantId() == null) {
      return Optional.empty();
    }
    // Merchants are shared catalog entries: identity is the record itself.
    return merchants.findById(request.merchantId());
  }

  private static PaymentSignal activeCallSignal(DeviceCallSignal deviceSignal) {
    if (deviceSignal != null && Boolean.TRUE.equals(deviceSignal.activeCall())) {
      return new PaymentSignal(PaymentSignalType.ACTIVE_CALL, true,
          SignalSource.DEVICE, null, "Device reported an active phone call.");
    }
    if (deviceSignal != null && Boolean.FALSE.equals(deviceSignal.activeCall())) {
      return new PaymentSignal(PaymentSignalType.ACTIVE_CALL, false,
          SignalSource.DEVICE, null, "Device reported no active call.");
    }
    String reason =
        deviceSignal != null && deviceSignal.unavailabilityReason() != null
            ? deviceSignal.unavailabilityReason()
            : "NOT_AVAILABLE";
    return new PaymentSignal(PaymentSignalType.ACTIVE_CALL, false,
        SignalSource.NOT_AVAILABLE, reason, "No trusted call-state signal is available.");
  }

  private static PaymentSignal unexpectedInvoice(
      DeterministicContentAnalyzer.ContentAnalysis content, SignalCollectionRequest request) {
    if (content.unexpectedInvoice()) {
      return new PaymentSignal(
          PaymentSignalType.UNEXPECTED_INVOICE,
          true,
          SignalSource.PAYMENT_CONTEXT,
          null,
          content.invoiceEvidence());
    }
    if (!hasText(request.contentText()) && !hasText(request.contentUrl())) {
      return unavailable(
          PaymentSignalType.UNEXPECTED_INVOICE,
          SignalSource.PAYMENT_CONTEXT,
          "INSUFFICIENT_CONTEXT",
          "No message or invoice text was supplied.");
    }
    return new PaymentSignal(
        PaymentSignalType.UNEXPECTED_INVOICE,
        false,
        SignalSource.PAYMENT_CONTEXT,
        null,
        "No unexpected-invoice pattern was found in the supplied context.");
  }

  private static PaymentSignal suspiciousUrl(
      DeterministicContentAnalyzer.ContentAnalysis content, SignalCollectionRequest request) {
    if (content.suspiciousUrl()) {
      return new PaymentSignal(
          PaymentSignalType.SUSPICIOUS_URL,
          true,
          SignalSource.URL_REPUTATION,
          "DETERMINISTIC_URL_STRUCTURE",
          content.urlEvidence());
    }
    if (!hasText(request.contentUrl())) {
      return unavailable(
          PaymentSignalType.SUSPICIOUS_URL,
          SignalSource.URL_REPUTATION,
          "NO_URL_IN_CONTENT",
          "No payment URL was supplied for structural analysis.");
    }
    return new PaymentSignal(
        PaymentSignalType.SUSPICIOUS_URL,
        false,
        SignalSource.URL_REPUTATION,
        null,
        "No structural URL red flags were found.");
  }

  private static PaymentSignal urgentLanguage(
      DeterministicContentAnalyzer.ContentAnalysis content, SignalCollectionRequest request) {
    if (content.urgent()) {
      return new PaymentSignal(
          PaymentSignalType.URGENT_LANGUAGE,
          true,
          SignalSource.PAYMENT_CONTEXT,
          null,
          content.urgentEvidence());
    }
    if (!hasText(request.contentText())) {
      return unavailable(
          PaymentSignalType.URGENT_LANGUAGE,
          SignalSource.NOT_AVAILABLE,
          "CONTENT_UNAVAILABLE",
          "No message text was supplied for language analysis.");
    }
    return new PaymentSignal(
        PaymentSignalType.URGENT_LANGUAGE,
        false,
        SignalSource.PAYMENT_CONTEXT,
        null,
        "No urgent or pressuring language was found.");
  }

  /**
   * A single AI/LLM finding is never required to identify a scam pattern:
   * independent local signals are combined here. This is deliberately
   * conservative and only fires when multiple indicators co-occur.
   */
  private static PaymentSignal socialEngineering(
      DeterministicContentAnalyzer.ContentAnalysis content,
      PaymentSignal newRecipientSignal,
      PaymentSignal unknownMerchantSignal,
      PaymentSignal activeCallSignal,
      SignalCollectionRequest request) {
    boolean newCounterparty = newRecipientSignal.detected() || unknownMerchantSignal.detected();
    boolean activeCall = activeCallSignal.detected();
    boolean combination =
        content.scamPattern()
            || (content.paymentInstruction() && (newCounterparty || activeCall))
            || (content.unexpectedInvoice()
                && newCounterparty
                && (content.urgent() || activeCall))
            || (activeCall
                && (content.urgent()
                    || content.unexpectedInvoice()
                    || content.paymentInstruction()));
    if (combination) {
      String evidence =
          content.scamEvidence() != null
              ? content.scamEvidence()
              : "Multiple independent scam indicators co-occur with a new/unknown counterparty"
                  + (activeCall ? " and an active call." : ".");
      return new PaymentSignal(
          PaymentSignalType.SOCIAL_ENGINEERING_DETECTED,
          true,
          SignalSource.PAYMENT_CONTEXT,
          null,
          evidence);
    }
    if (!hasText(request.contentText())
        && !hasText(request.contentUrl())
        && !activeCall
        && !newCounterparty) {
      return unavailable(
          PaymentSignalType.SOCIAL_ENGINEERING_DETECTED,
          SignalSource.NOT_AVAILABLE,
          "INSUFFICIENT_CONTEXT",
          "Not enough independent context to evaluate a scam pattern.");
    }
    return new PaymentSignal(
        PaymentSignalType.SOCIAL_ENGINEERING_DETECTED,
        false,
        SignalSource.PAYMENT_CONTEXT,
        null,
        "No multi-signal social-engineering pattern was found.");
  }

  private static PaymentSignal missingContext(
      SignalCollectionRequest request, DeviceCallSignal deviceSignal) {
    if (request.amount() == null) {
      return unavailable(
          PaymentSignalType.MISSING_CONTEXT,
          SignalSource.PAYMENT_CONTEXT,
          "AMOUNT_UNAVAILABLE",
          "No amount was supplied.");
    }
    if (request.amount().compareTo(HIGH_VALUE_CONTEXT_THRESHOLD) < 0) {
      return new PaymentSignal(
          PaymentSignalType.MISSING_CONTEXT,
          false,
          SignalSource.PAYMENT_CONTEXT,
          "BELOW_HIGH_VALUE_THRESHOLD",
          "Payment is below the high-value context threshold.");
    }
    boolean contentMissing =
        !hasText(request.contentText()) && !hasText(request.contentUrl());
    boolean deviceUnknown = deviceSignal == null || deviceSignal.activeCall() == null;
    if (contentMissing && deviceUnknown) {
      return new PaymentSignal(
          PaymentSignalType.MISSING_CONTEXT,
          true,
          SignalSource.PAYMENT_CONTEXT,
          "HIGH_VALUE_CONTEXT_MISSING",
          "High-value payment has no message/link and no trusted device observation.");
    }
    return new PaymentSignal(
        PaymentSignalType.MISSING_CONTEXT,
        false,
        SignalSource.PAYMENT_CONTEXT,
        "CONTEXT_SUFFICIENT",
        "At least one independent context signal is available.");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static PaymentSignal unavailable(
      PaymentSignalType type, SignalSource source, String reason, String evidence) {
    return new PaymentSignal(type, false, source, reason, evidence);
  }
}
