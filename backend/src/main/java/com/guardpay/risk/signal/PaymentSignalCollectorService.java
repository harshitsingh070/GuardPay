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
import com.guardpay.risk.blocklist.RecipientBlocklistRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  /**
   * A first payment to an unknown recipient has no trusted relationship to
   * justify missing context, so the context requirement starts lower.
   */
  static final BigDecimal NEW_RECIPIENT_CONTEXT_THRESHOLD = new BigDecimal("500.00");

  /** Structuring detection: several sub-threshold payments in a short window. */
  static final BigDecimal SPLIT_PAYMENT_AGGREGATE_THRESHOLD = new BigDecimal("1000.00");

  static final BigDecimal SPLIT_PAYMENT_SINGLE_CAP = new BigDecimal("1000.00");

  static final int SPLIT_PAYMENT_MIN_ATTEMPTS = 3;

  public static final Duration SPLIT_PAYMENT_WINDOW = Duration.ofHours(24);

  /**
   * A country change inside this window is treated as impossible travel. This
   * is a conservative proxy: without precise distance data, only a same-day
   * cross-country change is flagged.
   */
  public static final Duration IMPOSSIBLE_TRAVEL_WINDOW = Duration.ofHours(6);

  /** Multi-window velocity tiers (attempt threshold + window). */
  public static final int HOURLY_VELOCITY_THRESHOLD = 5;

  public static final Duration HOURLY_VELOCITY_WINDOW = Duration.ofHours(1);

  public static final int DAILY_VELOCITY_THRESHOLD = 10;

  public static final Duration DAILY_VELOCITY_WINDOW = Duration.ofHours(24);

  static final UUID NO_EXCLUDED_TRANSACTION = new UUID(0L, 0L);

  private final UserRepository users;
  private final RecipientRepository recipients;
  private final MerchantRepository merchants;
  private final TransactionRepository transactions;
  private final DeterministicContentAnalyzer contentAnalyzer;
  private final RecipientHistoryService recipientHistory;
  private final RecipientRiskEvaluator recipientRiskEvaluator;
  private final RecipientBlocklistRepository blocklist;

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
        new RecipientRiskEvaluator(),
        null);
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
        new RecipientRiskEvaluator(),
        null);
  }

  /** Legacy test/manual wiring without blocklist intelligence. */
  public PaymentSignalCollectorService(
      UserRepository users,
      RecipientRepository recipients,
      MerchantRepository merchants,
      TransactionRepository transactions,
      DeterministicContentAnalyzer contentAnalyzer,
      RecipientHistoryService recipientHistory,
      RecipientRiskEvaluator recipientRiskEvaluator) {
    this(
        users,
        recipients,
        merchants,
        transactions,
        contentAnalyzer,
        recipientHistory,
        recipientRiskEvaluator,
        null);
  }

  @Autowired
  public PaymentSignalCollectorService(
      UserRepository users,
      RecipientRepository recipients,
      MerchantRepository merchants,
      TransactionRepository transactions,
      DeterministicContentAnalyzer contentAnalyzer,
      RecipientHistoryService recipientHistory,
      RecipientRiskEvaluator recipientRiskEvaluator,
      RecipientBlocklistRepository blocklist) {
    this.users = users;
    this.recipients = recipients;
    this.merchants = merchants;
    this.transactions = transactions;
    this.contentAnalyzer = contentAnalyzer;
    this.recipientHistory = recipientHistory;
    this.recipientRiskEvaluator = recipientRiskEvaluator;
    this.blocklist = blocklist;
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
    OffsetDateTime evaluationTime = OffsetDateTime.now(ZoneOffset.UTC);

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
                        request.currency(),
                        evaluationTime))
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
    signals.add(velocityTier(
        PaymentSignalType.VELOCITY_HOURLY,
        HOURLY_VELOCITY_THRESHOLD,
        HOURLY_VELOCITY_WINDOW,
        request,
        user));
    signals.add(velocityTier(
        PaymentSignalType.VELOCITY_DAILY,
        DAILY_VELOCITY_THRESHOLD,
        DAILY_VELOCITY_WINDOW,
        request,
        user));
    signals.add(blocklistedRecipient(request, user));
    PaymentSignal newRecipientSignal = recipientRiskEvaluator.newRecipient(recipientContext);
    signals.add(newRecipientSignal);
    PaymentSignal unknownMerchantSignal = unknownMerchant(request, user);
    signals.add(unknownMerchantSignal);
    signals.add(unexpectedInvoice(content, request));
    signals.add(suspiciousUrl(content, request));
    PaymentSignal callSignal = activeCallSignal(deviceSignal);
    signals.add(callSignal);
    signals.add(newDevice(request, user, evaluationTime));
    signals.add(newGeo(request, user, evaluationTime));
    signals.add(impossibleTravel(request, user, evaluationTime));
    signals.add(vpnProxy(request));
    signals.add(urgentLanguage(content, request));
    signals.add(
        socialEngineering(
            content, newRecipientSignal, unknownMerchantSignal, callSignal, request));
    signals.add(knownRecipientScam(content, recipientContext, request));
    signals.add(splitPayment(request, user, recipient, recipientContext, evaluationTime));
    signals.add(missingContext(request, deviceSignal, newRecipientSignal.detected()));
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

  /**
   * Multi-window velocity: a tier fires when this attempt is at least the
   * threshold-th attempt inside the window. Counting includes the current
   * attempt; unavailable user context never produces a false "safe" signal.
   */
  private PaymentSignal velocityTier(
      PaymentSignalType type,
      int threshold,
      Duration window,
      SignalCollectionRequest request,
      User user) {
    if (user == null) {
      return unavailable(
          type,
          SignalSource.TRANSACTION_HISTORY,
          "USER_UNAVAILABLE",
          "No user context is available for a velocity check.");
    }
    OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(window);
    UUID exclude =
        request.excludeTransactionId() == null
            ? NO_EXCLUDED_TRANSACTION
            : request.excludeTransactionId();
    long prior =
        transactions.countAttemptsSince(user, request.currency(), cutoff, exclude);
    long total = prior + 1;
    boolean hit = total >= threshold;
    String windowLabel =
        window.toHours() >= 24 ? (window.toHours() / 24) + " day" : window.toHours() + " hour";
    return new PaymentSignal(
        type,
        hit,
        SignalSource.TRANSACTION_HISTORY,
        null,
        hit
            ? total + " payment attempts were made in the last " + windowLabel + "."
            : "No unusual payment velocity in the last " + windowLabel + ".");
  }

  /**
   * Counterparty reputation: a blocklisted recipient adds hard evidence. It is
   * never the sole basis for a decision, and the blocklist is owner-scoped.
   */
  private PaymentSignal blocklistedRecipient(SignalCollectionRequest request, User user) {
    if (user == null) {
      return unavailable(
          PaymentSignalType.BLOCKLISTED_RECIPIENT,
          SignalSource.TRANSACTION_HISTORY,
          "USER_UNAVAILABLE",
          "No user context is available for blocklist matching.");
    }
    if (request.recipientId() == null) {
      return unavailable(
          PaymentSignalType.BLOCKLISTED_RECIPIENT,
          SignalSource.TRANSACTION_HISTORY,
          "RECIPIENT_IDENTITY_UNAVAILABLE",
          "No reliable recipient identity was provided.");
    }
    if (blocklist == null) {
      return unavailable(
          PaymentSignalType.BLOCKLISTED_RECIPIENT,
          SignalSource.TRANSACTION_HISTORY,
          "BLOCKLIST_UNAVAILABLE",
          "Counterparty blocklist data is not available.");
    }
    boolean hit =
        blocklist.existsByUserIdAndRecipientId(user.getId(), request.recipientId());
    return new PaymentSignal(
        PaymentSignalType.BLOCKLISTED_RECIPIENT,
        hit,
        SignalSource.TRANSACTION_HISTORY,
        null,
        hit
            ? "This recipient has been reported and added to the fraud blocklist."
            : "This recipient is not on the fraud blocklist.");
  }

  private Optional<Recipient> identifiedRecipient(SignalCollectionRequest request, User user) {
    if (user == null || user.getId() == null || request.recipientId() == null) {
      return Optional.empty();
    }
    return recipients
        .findByIdAndUserId(request.recipientId(), user.getId())
        .filter(
            recipient ->
                recipient.getUser() != null
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

  /**
   * A device is "new" only when the account has previous device observations
   * and none match this attempt's trusted identity. A first-ever payment is
   * reported unavailable rather than new, so onboarding is not punished.
   */
  private PaymentSignal newDevice(
      SignalCollectionRequest request, User user, OffsetDateTime evaluationTime) {
    if (user == null) {
      return unavailable(
          PaymentSignalType.NEW_DEVICE,
          SignalSource.TRANSACTION_HISTORY,
          "USER_UNAVAILABLE",
          "No user context is available for device history.");
    }
    String fingerprint = trimmedOrNull(request.deviceFingerprint());
    UUID deviceId = request.deviceId();
    if (fingerprint == null && deviceId == null) {
      return unavailable(
          PaymentSignalType.NEW_DEVICE,
          SignalSource.DEVICE,
          "DEVICE_IDENTITY_UNAVAILABLE",
          "No trusted device identity was supplied for this payment.");
    }
    if (transactions.countDeviceObservations(user, evaluationTime) <= 0) {
      return unavailable(
          PaymentSignalType.NEW_DEVICE,
          SignalSource.TRANSACTION_HISTORY,
          "NO_PRIOR_DEVICE_HISTORY",
          "No earlier device observations exist for this account.");
    }
    boolean known =
        (fingerprint != null
                && transactions
                    .findTopByUserAndDeviceFingerprintOrderByTransactionTimestampDesc(
                        user, fingerprint)
                    .isPresent())
            || (deviceId != null
                && transactions
                    .findTopByUserAndDeviceIdOrderByTransactionTimestampDesc(user, deviceId)
                    .isPresent());
    return new PaymentSignal(
        PaymentSignalType.NEW_DEVICE,
        !known,
        SignalSource.TRANSACTION_HISTORY,
        null,
        known
            ? "This device has been used for the account before."
            : "This device has not been seen for this account before.");
  }

  /**
   * A country is "new" only when the account has earlier geolocated payments
   * and none match. Geo data may be unavailable (no provider, private IP), in
   * which case the signal reports a reason instead of guessing.
   */
  private PaymentSignal newGeo(
      SignalCollectionRequest request, User user, OffsetDateTime evaluationTime) {
    if (user == null) {
      return unavailable(
          PaymentSignalType.NEW_GEO,
          SignalSource.TRANSACTION_HISTORY,
          "USER_UNAVAILABLE",
          "No user context is available for location history.");
    }
    String country = normalizedCountry(request.geoCountry());
    if (country == null) {
      return unavailable(
          PaymentSignalType.NEW_GEO,
          SignalSource.NETWORK,
          "GEO_UNAVAILABLE",
          "No trusted country was resolved for this payment.");
    }
    if (transactions.countGeoObservations(user, evaluationTime) <= 0) {
      return unavailable(
          PaymentSignalType.NEW_GEO,
          SignalSource.TRANSACTION_HISTORY,
          "NO_PRIOR_GEO_HISTORY",
          "No earlier geolocated payments exist for this account.");
    }
    boolean known =
        transactions
            .findTopByUserAndGeoCountryIgnoreCaseAndTransactionTimestampBeforeOrderByTransactionTimestampDesc(
                user, country, evaluationTime)
            .isPresent();
    return new PaymentSignal(
        PaymentSignalType.NEW_GEO,
        !known,
        SignalSource.TRANSACTION_HISTORY,
        null,
        known
            ? "This account has paid from " + country + " before."
            : "This account has no earlier payment from " + country + ".");
  }

  private PaymentSignal impossibleTravel(
      SignalCollectionRequest request, User user, OffsetDateTime evaluationTime) {
    if (user == null) {
      return unavailable(
          PaymentSignalType.IMPOSSIBLE_TRAVEL,
          SignalSource.TRANSACTION_HISTORY,
          "USER_UNAVAILABLE",
          "No user context is available for travel analysis.");
    }
    String country = normalizedCountry(request.geoCountry());
    if (country == null) {
      return unavailable(
          PaymentSignalType.IMPOSSIBLE_TRAVEL,
          SignalSource.NETWORK,
          "GEO_UNAVAILABLE",
          "No trusted country was resolved for this payment.");
    }
    Optional<Transaction> previous =
        transactions
            .findTopByUserAndGeoCountryIsNotNullAndTransactionTimestampBeforeOrderByTransactionTimestampDesc(
                user, evaluationTime);
    if (previous.isEmpty() || previous.get().getGeoCountry() == null) {
      return unavailable(
          PaymentSignalType.IMPOSSIBLE_TRAVEL,
          SignalSource.TRANSACTION_HISTORY,
          "NO_PRIOR_GEO_HISTORY",
          "No earlier geolocated payment exists to compare against.");
    }
    Transaction prior = previous.get();
    String priorCountry = normalizedCountry(prior.getGeoCountry());
    if (priorCountry != null && priorCountry.equalsIgnoreCase(country)) {
      return new PaymentSignal(
          PaymentSignalType.IMPOSSIBLE_TRAVEL,
          false,
          SignalSource.TRANSACTION_HISTORY,
          null,
          "The previous payment was from the same country.");
    }
    OffsetDateTime priorAt = prior.getTransactionTimestamp();
    boolean withinWindow =
        priorAt != null && priorAt.isAfter(evaluationTime.minus(IMPOSSIBLE_TRAVEL_WINDOW));
    return new PaymentSignal(
        PaymentSignalType.IMPOSSIBLE_TRAVEL,
        withinWindow,
        SignalSource.TRANSACTION_HISTORY,
        null,
        withinWindow
            ? "The previous payment was from " + priorCountry + " less than "
                + IMPOSSIBLE_TRAVEL_WINDOW.toHours() + " hours ago; this one is from "
                + country + "."
            : "The last geolocated payment was outside the impossible-travel window.");
  }

  private static PaymentSignal vpnProxy(SignalCollectionRequest request) {
    if (request.vpnOrProxy() == null) {
      return unavailable(
          PaymentSignalType.VPN_PROXY,
          SignalSource.NETWORK,
          "NETWORK_REPUTATION_UNAVAILABLE",
          "No network reputation result was available for this connection.");
    }
    return new PaymentSignal(
        PaymentSignalType.VPN_PROXY,
        request.vpnOrProxy(),
        SignalSource.NETWORK,
        null,
        request.vpnOrProxy()
            ? "The connection is routed through a VPN, proxy, or hosting provider."
            : "The connection does not look like a VPN or proxy.");
  }

  private static String trimmedOrNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String normalizedCountry(String value) {
    String trimmed = trimmedOrNull(value);
    return trimmed == null ? null : trimmed.toUpperCase(java.util.Locale.ROOT);
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

  /**
   * A known recipient can still be the victim of account takeover. A new
   * high-risk payment instruction, a lookalike link on an invoice, or an
   * urgent explicit payment request is not normal history behaviour.
   */
  private static PaymentSignal knownRecipientScam(
      DeterministicContentAnalyzer.ContentAnalysis content,
      RecipientRiskContext recipientContext,
      SignalCollectionRequest request) {
    if (!recipientContext.identified()) {
      return unavailable(
          PaymentSignalType.KNOWN_RECIPIENT_SCAM,
          SignalSource.TRANSACTION_HISTORY,
          "RECIPIENT_IDENTITY_UNAVAILABLE",
          "No reliable recipient identity was provided.");
    }
    if (!recipientContext.hasCompletedHistory()) {
      return new PaymentSignal(
          PaymentSignalType.KNOWN_RECIPIENT_SCAM,
          false,
          SignalSource.TRANSACTION_HISTORY,
          "NO_PRIOR_RECIPIENT_HISTORY",
          "This recipient has no established payment history.");
    }

    boolean highRiskInstruction =
        content.paymentInstruction()
            && (content.urgent() || content.unexpectedInvoice() || content.suspiciousUrl());
    boolean lookalikeInvoice = content.unexpectedInvoice() && content.suspiciousUrl();
    boolean urgentPaymentRequest =
        content.explicitPaymentRequest() && content.urgent() && !content.unexpectedInvoice();
    boolean hit =
        content.scamPattern() || highRiskInstruction || lookalikeInvoice || urgentPaymentRequest;
    return new PaymentSignal(
        PaymentSignalType.KNOWN_RECIPIENT_SCAM,
        hit,
        SignalSource.PAYMENT_CONTEXT,
        null,
        hit
            ? "This is an established recipient, but the request matches a compromised-payee scam pattern."
            : "No compromised-payee scam pattern was found for this established recipient.");
  }

  /**
   * Structuring check: the current sub-$1,000 payment plus at least two other
   * sub-$1,000 attempts to the same recipient total at least $1,000 within a
   * day. This catches attempts to stay below the high-value context rule.
   */
  private PaymentSignal splitPayment(
      SignalCollectionRequest request,
      User user,
      Optional<Recipient> recipient,
      RecipientRiskContext recipientContext,
      OffsetDateTime evaluationTime) {
    if (request.amount() == null) {
      return unavailable(
          PaymentSignalType.SPLIT_PAYMENT,
          SignalSource.TRANSACTION_HISTORY,
          "AMOUNT_UNAVAILABLE",
          "No amount was supplied.");
    }
    if (user == null) {
      return unavailable(
          PaymentSignalType.SPLIT_PAYMENT,
          SignalSource.TRANSACTION_HISTORY,
          "USER_UNAVAILABLE",
          "No user context is available for a split-payment check.");
    }
    if (!recipientContext.identified() || recipient.isEmpty() || recipient.get().getId() == null) {
      return unavailable(
          PaymentSignalType.SPLIT_PAYMENT,
          SignalSource.TRANSACTION_HISTORY,
          "RECIPIENT_IDENTITY_UNAVAILABLE",
          "Split payments require a reliable recipient identity.");
    }
    if (request.amount().compareTo(SPLIT_PAYMENT_SINGLE_CAP) >= 0) {
      return new PaymentSignal(
          PaymentSignalType.SPLIT_PAYMENT,
          false,
          SignalSource.TRANSACTION_HISTORY,
          "ABOVE_SPLIT_PAYMENT_CAP",
          "This payment alone is already above the split-payment cap.");
    }

    UUID recipientId = recipient.get().getId();
    List<Transaction> recent =
        transactions.findByUserAndTransactionTimestampAfter(
            user, evaluationTime.minus(SPLIT_PAYMENT_WINDOW));
    List<Transaction> sameRecipient =
        (recent == null ? List.<Transaction>of() : recent).stream()
            .filter(transaction -> matchesCurrency(transaction, request.currency()))
            .filter(PaymentSignalCollectorService::hasValidAmount)
            .filter(transaction -> !isExcluded(transaction, request.excludeTransactionId()))
            .filter(
                transaction ->
                    transaction.getRecipient() != null
                        && recipientId.equals(transaction.getRecipient().getId()))
            .filter(
                transaction ->
                    transaction.getAmount().compareTo(SPLIT_PAYMENT_SINGLE_CAP) < 0)
            .toList();
    BigDecimal total =
        sameRecipient.stream()
            .map(Transaction::getAmount)
            .reduce(request.amount(), BigDecimal::add);
    int attempts = sameRecipient.size() + 1;
    boolean hit =
        attempts >= SPLIT_PAYMENT_MIN_ATTEMPTS
            && total.compareTo(SPLIT_PAYMENT_AGGREGATE_THRESHOLD) >= 0;
    return new PaymentSignal(
        PaymentSignalType.SPLIT_PAYMENT,
        hit,
        SignalSource.TRANSACTION_HISTORY,
        null,
        hit
            ? attempts
                + " payments to this recipient total "
                + total.toPlainString()
                + " in the last "
                + SPLIT_PAYMENT_WINDOW.toHours()
                + " hours."
            : "Recent payments to this recipient do not show a split-payment pattern.");
  }

  private static PaymentSignal missingContext(
      SignalCollectionRequest request,
      DeviceCallSignal deviceSignal,
      boolean newRecipient) {
    if (request.amount() == null) {
      return unavailable(
          PaymentSignalType.MISSING_CONTEXT,
          SignalSource.PAYMENT_CONTEXT,
          "AMOUNT_UNAVAILABLE",
          "No amount was supplied.");
    }
    BigDecimal threshold =
        newRecipient ? NEW_RECIPIENT_CONTEXT_THRESHOLD : HIGH_VALUE_CONTEXT_THRESHOLD;
    if (request.amount().compareTo(threshold) < 0) {
      return new PaymentSignal(
          PaymentSignalType.MISSING_CONTEXT,
          false,
          SignalSource.PAYMENT_CONTEXT,
          "BELOW_CONTEXT_THRESHOLD",
          "Payment is below the context threshold for this recipient.");
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
          "Payment has no message/link and no trusted device observation.");
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
