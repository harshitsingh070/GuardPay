package com.guardpay.behavior;

import com.guardpay.behavior.dto.BehavioralAnalysisRequest;
import com.guardpay.behavior.dto.BehavioralAnalysisResponse;
import com.guardpay.behavior.dto.BehavioralFindingDto;
import com.guardpay.behavior.dto.BehavioralProfileDto;
import com.guardpay.behavior.dto.BehavioralProfileSummaryDto;
import com.guardpay.behavior.dto.HighValueBaselineDto;
import com.guardpay.behavior.highvalue.FirstTimeHighValueAssessment;
import com.guardpay.behavior.highvalue.FirstTimeHighValueDetectionEngine;
import com.guardpay.recipient.Recipient;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.StaleObjectStateException;
import org.hibernate.StaleStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Computes per-user behavioral statistics from eligible COMPLETED
 * transaction history across three rolling windows (7, 30, 90 days).
 *
 * <p>Analysis ({@link #analyze}) is strictly read-only: it never writes to
 * the profile tables. Profile persistence happens only through
 * {@link #updateProfileAfterOutcome(UUID, Transaction, OffsetDateTime)}, which
 * is called when a transaction reaches a terminal state (COMPLETED, FAILED,
 * or CANCELLED).
 *
 * <p>Amount statistics are maintained separately per currency. Time-of-day
   analysis uses the user's configured IANA timezone when available.
 *
 * <p>Suspicious transactions (high-risk decisions: STOP, or HIGH/CRITICAL
   behavioral findings) are excluded from baseline learning to prevent profile
   poisoning.
 */
@Service
public class BehavioralProfileService {

  private static final Logger log = LoggerFactory.getLogger(BehavioralProfileService.class);

  /** Maximum retries for optimistic lock conflicts during profile update. */
  public static final int MAX_RETRIES = 3;

  /** Fewer than this many eligible transactions = insufficient baseline. */
  public static final int MIN_TRANSACTIONS_FOR_BASELINE = 5;

  /** 5-19 transactions = initializing profile. */
  public static final int MIN_TRANSACTIONS_FOR_INITIALIZING = 5;

  /** 20+ transactions = eligible for established profile. */
  public static final int MIN_TRANSACTIONS_FOR_ESTABLISHED = 20;

  /** MAD of zero means all historical amounts are identical. */
  private static final BigDecimal ZERO_MAD_THRESHOLD = new BigDecimal("0.01");

  /** Minimum meaningful deviation ratio to avoid flagging trivial variations. */
  private static final BigDecimal MIN_MEANINGFUL_DEVIATION_RATIO = new BigDecimal("1.5");

  /**
   * Robust-outlier guard for baseline learning. A completed payment this far
   * outside the established distribution is not learned unless the user
   * explicitly cleared a VERIFY challenge. This prevents a single
   * account-takeover payment from poisoning the profile.
   */
  private static final BigDecimal POISONING_MODIFIED_Z_THRESHOLD = new BigDecimal("5.0");

  private static final BigDecimal POISONING_RATIO_THRESHOLD = new BigDecimal("5.0");

  private static final long RETRY_BACKOFF_MILLIS = 20L;

  private final TransactionRepository transactions;
  private final UserBehaviorProfileRepository profiles;
  private final UserRecipientBehaviorRepository recipientBehaviors;
  private final UserHourlyBehaviorRepository hourlyBehaviors;
  private final UserGeoBehaviorRepository geoBehaviors;
  private final UserDeviceBehaviorRepository deviceBehaviors;
  private final EntityManager entityManager;
  private final BehavioralOutcomeEventRepository outcomeEvents;
  private final TransactionTemplate requiresNewTransaction;
  private final FirstTimeHighValueDetectionEngine highValueEngine;

  public BehavioralProfileService(
      TransactionRepository transactions,
      UserBehaviorProfileRepository profiles,
      UserRecipientBehaviorRepository recipientBehaviors,
      UserHourlyBehaviorRepository hourlyBehaviors,
      UserGeoBehaviorRepository geoBehaviors,
      UserDeviceBehaviorRepository deviceBehaviors,
      EntityManager entityManager) {
    this(
        transactions,
        profiles,
        recipientBehaviors,
        hourlyBehaviors,
        geoBehaviors,
        deviceBehaviors,
        entityManager,
        null,
        null,
        null);
  }

  public BehavioralProfileService(
      TransactionRepository transactions,
      UserBehaviorProfileRepository profiles,
      UserRecipientBehaviorRepository recipientBehaviors,
      UserHourlyBehaviorRepository hourlyBehaviors,
      UserGeoBehaviorRepository geoBehaviors,
      UserDeviceBehaviorRepository deviceBehaviors,
      EntityManager entityManager,
      BehavioralOutcomeEventRepository outcomeEvents,
      PlatformTransactionManager transactionManager) {
    this(
        transactions,
        profiles,
        recipientBehaviors,
        hourlyBehaviors,
        geoBehaviors,
        deviceBehaviors,
        entityManager,
        outcomeEvents,
        transactionManager,
        new FirstTimeHighValueDetectionEngine());
  }

  @Autowired
  public BehavioralProfileService(
      TransactionRepository transactions,
      UserBehaviorProfileRepository profiles,
      UserRecipientBehaviorRepository recipientBehaviors,
      UserHourlyBehaviorRepository hourlyBehaviors,
      UserGeoBehaviorRepository geoBehaviors,
      UserDeviceBehaviorRepository deviceBehaviors,
      EntityManager entityManager,
      BehavioralOutcomeEventRepository outcomeEvents,
      PlatformTransactionManager transactionManager,
      @Autowired(required = false) FirstTimeHighValueDetectionEngine highValueEngine) {
    this.transactions = transactions;
    this.profiles = profiles;
    this.recipientBehaviors = recipientBehaviors;
    this.hourlyBehaviors = hourlyBehaviors;
    this.geoBehaviors = geoBehaviors;
    this.deviceBehaviors = deviceBehaviors;
    this.entityManager = entityManager;
    this.outcomeEvents = outcomeEvents;
    this.highValueEngine = highValueEngine;
    if (transactionManager == null) {
      this.requiresNewTransaction = null;
    } else {
      TransactionTemplate template = new TransactionTemplate(transactionManager);
      template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      this.requiresNewTransaction = template;
    }
  }

  /**
   * Computes behavioral anomaly findings for an incoming payment against the
   * user's persisted profile. Strictly read-only: never writes to profile
   * tables. Profile persistence happens only through
   * {@link #updateProfileAfterOutcome(UUID, Transaction, OffsetDateTime)}.
   *
   * <p>Does not include the current transaction in its own baseline.
   */
  @Transactional(readOnly = true)
  public BehavioralAnalysisResponse analyze(
      BehavioralAnalysisRequest request, UUID excludeTransactionId) {
    if (request.userId() == null) {
      return new BehavioralAnalysisResponse(
          null, "INSUFFICIENT_DATA", 0, null, null, List.of(),
          "INSUFFICIENT_DATA", false);
    }

    OffsetDateTime asOf = request.asOf() != null ? request.asOf() : OffsetDateTime.now();

    // Load existing profile (read-only)
    Optional<UserBehaviorProfile> existingProfile =
        profiles.findByUserId(request.userId());

    // Load eligible history: COMPLETED only, before asOf, excluding current txn
    List<Transaction> history = loadEligibleHistory(request.userId(), asOf, excludeTransactionId);

    ProfileConfidence confidence = assessConfidence(history, request.userId());

    List<BehavioralFindingDto> findings = new ArrayList<>();

    if (confidence.status() == UserBehaviorProfile.ProfileStatus.INSUFFICIENT_DATA) {
      return new BehavioralAnalysisResponse(
          request.userId(),
          "INSUFFICIENT_DATA",
          history.size(),
          null,
          null,
          findings,
          "INSUFFICIENT_DATA",
          false);
    }

    // Amount behavior: compute per currency, compare only matching currency
    Map<String, AmountStats> amountStatsByCurrencyWindow = new HashMap<>();
    String requestCurrency = request.currency() != null ? request.currency() : "USD";

    for (BehavioralWindow window : BehavioralWindow.values()) {
      List<Transaction> windowHistory =
          history.stream()
              .filter(t -> t.getTransactionTimestamp() != null
                  && t.getTransactionTimestamp()
                      .isAfter(asOf.minus(window.getDays(), ChronoUnit.DAYS)))
              .filter(t -> requestCurrency.equals(t.getCurrency()))
              .toList();
      AmountStats stats = computeAmountStats(windowHistory);
      if (stats != null && stats.count() > 0) {
        amountStatsByCurrencyWindow.put(window.getLabel(), stats);
      }
    }

    // Use the most appropriate window for the request's currency
    BehavioralWindow primaryWindow = selectPrimaryWindow(amountStatsByCurrencyWindow);
    AmountStats primaryAmountStats = amountStatsByCurrencyWindow.get(primaryWindow.getLabel());

    // Filter history to the primary window for all non-amount analyses so that
    // every behavioral finding identifies the actual window used.
    OffsetDateTime windowStart = asOf.minus(primaryWindow.getDays(), ChronoUnit.DAYS);
    List<Transaction> windowHistory = history.stream()
        .filter(t -> t.getTransactionTimestamp() != null
            && t.getTransactionTimestamp().isAfter(windowStart))
        .toList();

    // Amount deviation finding (currency-specific)
    if (request.amount() != null
        && primaryAmountStats != null
        && primaryAmountStats.count() >= MIN_TRANSACTIONS_FOR_BASELINE) {
      BehavioralFindingDto amountFinding =
          analyzeAmountDeviation(
              request.amount(),
              primaryAmountStats,
              primaryWindow.getLabel(),
              confidence.status().toString(),
              requestCurrency);
      if (amountFinding != null) {
        findings.add(amountFinding);
      }
    }

    // First-Time High-Value Payment Detection Engine (user-specific baseline analysis)
    if (highValueEngine != null && request.amount() != null) {
      FirstTimeHighValueAssessment highValueAssessment =
          highValueEngine.evaluate(
              request.userId(),
              request.amount(),
              requestCurrency,
              request.recipientId(),
              history,
              excludeTransactionId,
              asOf,
              confidence.status().toString());

      if (highValueAssessment != null && highValueAssessment.detected()) {
        findings.add(
            new BehavioralFindingDto(
                highValueAssessment.classification().name(),
                highValueAssessment.title(),
                highValueAssessment.evidence(),
                highValueAssessment.severity(),
                highValueAssessment.confidence(),
                highValueAssessment.observedAmount(),
                highValueAssessment.expectedAmount(),
                highValueAssessment.amountRatio(),
                highValueAssessment.baselineWindow(),
                highValueAssessment.profileStatus(),
                highValueAssessment.evidence(),
                highValueAssessment.contributingFactors(),
                highValueAssessment.evaluatedAt()));
      }
    }

    // Time-of-day deviation (timezone-aware, window-filtered)
    if (request.transactionTimestamp() != null
        && confidence.sufficientForTime()) {
      String userTimezone = existingProfile.map(UserBehaviorProfile::getTimezone)
          .orElse(null);
      BehavioralFindingDto timeFinding =
          analyzeTimeDeviation(
              request.transactionTimestamp(),
              windowHistory,
              asOf,
              confidence.status().toString(),
              userTimezone,
              primaryWindow.getLabel());
      if (timeFinding != null) {
        findings.add(timeFinding);
      }
    }

    // Recipient behavior deviation (window-filtered)
    if (request.recipientId() != null && confidence.sufficientForRecipient()) {
      BehavioralFindingDto recipientFinding =
          analyzeRecipientDeviation(
              request.userId(),
              request.recipientId(),
              request.amount(),
              windowHistory,
              asOf,
              confidence.status().toString(),
              primaryWindow.getLabel());
      if (recipientFinding != null) {
        findings.add(recipientFinding);
      }
    }

    // Frequency anomaly (uses its own 7-day window)
    if (confidence.sufficientForFrequency()) {
      BehavioralFindingDto frequencyFinding =
          analyzeFrequencyAnomaly(windowHistory, asOf, confidence.status().toString());
      if (frequencyFinding != null) {
        findings.add(frequencyFinding);
      }
    }

    // Device behavior deviation (window-filtered)
    if ((request.deviceId() != null || request.deviceFingerprint() != null)
        && confidence.sufficientForDevice()) {
      BehavioralFindingDto deviceFinding =
          analyzeDeviceDeviation(
              request.userId(),
              request.deviceId(),
              request.deviceFingerprint(),
              windowHistory,
              confidence.status().toString(),
              primaryWindow.getLabel());
      if (deviceFinding != null) {
        findings.add(deviceFinding);
      }
    }

    // Geographic behavior deviation (window-filtered)
    if (request.geoCountry() != null && confidence.sufficientForGeo()) {
      BehavioralFindingDto geoFinding =
          analyzeGeoDeviation(
              request.userId(),
              request.geoCountry(),
              windowHistory,
              asOf,
              confidence.status().toString(),
              primaryWindow.getLabel());
      if (geoFinding != null) {
        findings.add(geoFinding);
      }
    }

    // Overall behavioral deviation (aggregate)
    String overall = computeOverallDeviation(findings);

    return new BehavioralAnalysisResponse(
        request.userId(),
        confidence.status().toString(),
        history.size(),
        null,
        confidence.transactionCount() != null
            ? BigDecimal.valueOf(confidence.transactionCount() * 0.01).toString()
            : null,
        findings,
        overall,
        true);
  }

  /**
   * Direct high-value evaluation method for controllers or callers wanting
   * full first-time high-value assessment details.
   */
  @Transactional(readOnly = true)
  public FirstTimeHighValueAssessment evaluateHighValue(
      UUID userId,
      BigDecimal amount,
      String currency,
      UUID recipientId,
      UUID excludeTransactionId,
      OffsetDateTime asOf) {
    if (highValueEngine == null || userId == null || amount == null) {
      return null;
    }
    OffsetDateTime effectiveAsOf = asOf != null ? asOf : OffsetDateTime.now();
    List<Transaction> history = loadEligibleHistory(userId, effectiveAsOf, excludeTransactionId);
    ProfileConfidence confidence = assessConfidence(history, userId);
    return highValueEngine.evaluate(
        userId,
        amount,
        currency,
        recipientId,
        history,
        excludeTransactionId,
        effectiveAsOf,
        confidence.status().toString());
  }

  /**
   * Retrieves the currency-specific high-value baseline statistics for the user.
   */
  @Transactional(readOnly = true)
  public HighValueBaselineDto getHighValueBaseline(UUID userId, String currency) {
    if (userId == null) {
      return null;
    }
    String reqCurrency = currency != null ? currency : "USD";
    OffsetDateTime now = OffsetDateTime.now();
    List<Transaction> history = loadEligibleHistory(userId, now, null).stream()
        .filter(t -> reqCurrency.equalsIgnoreCase(t.getCurrency()))
        .sorted(Comparator.comparing(Transaction::getTransactionTimestamp))
        .toList();

    ProfileConfidence confidence = assessConfidence(history, userId);
    int n = history.size();
    if (n < MIN_TRANSACTIONS_FOR_BASELINE) {
      return new HighValueBaselineDto(
          reqCurrency,
          confidence.status().toString(),
          n,
          null, null, null, null, null, null, null,
          false);
    }

    List<BigDecimal> amounts = history.stream().map(Transaction::getAmount).sorted().toList();
    BigDecimal median = median(amounts);
    BigDecimal max = amounts.get(n - 1);
    List<BigDecimal> deviations = amounts.stream().map(a -> a.subtract(median).abs()).sorted().toList();
    BigDecimal mad = median(deviations);
    BigDecimal p75 = percentile(amounts, 0.75);
    BigDecimal p90 = percentile(amounts, 0.90);
    BigDecimal p95 = percentile(amounts, 0.95);

    BigDecimal highValueThreshold = p90 != null ? p90 : max;
    boolean established = confidence.status() == UserBehaviorProfile.ProfileStatus.ESTABLISHED;

    return new HighValueBaselineDto(
        reqCurrency,
        confidence.status().toString(),
        n,
        median,
        mad,
        max,
        p75,
        p90,
        p95,
        highValueThreshold,
        established);
  }

  /**
   * Updates the user's behavioral profile after a transaction reaches a
   * terminal state. Suspicious or non-completed attempts are recorded as
   * non-learnable outcomes, so they can never enter the baseline.
   *
   * <p>This overload infers the outcome from the transaction status and is
   * kept for compatibility. New lifecycle callers should pass an explicit
   * outcome and source so refunds, reversals, and disputes are unambiguous.
   */
  public void updateProfileAfterOutcome(
      UUID userId,
      Transaction transaction,
      OffsetDateTime asOf,
      RecommendedAction recommendedAction) {
    if (transaction == null) {
      return;
    }
    BehavioralOutcome outcome = outcomeFor(transaction.getStatus());
    if (outcome == null) {
      return;
    }
    updateProfileAfterOutcome(
        userId,
        transaction,
        asOf,
        recommendedAction,
        outcome,
        false,
        "LEGACY_LIFECYCLE");
  }

  /**
   * Records one outcome and recomputes the baseline from eligible history.
   *
   * <p>Idempotency is enforced by the unique {@code (transaction, outcome)}
   * ledger row, not by a mutable "last transaction" pointer. Each attempt runs
   * in a fresh {@code REQUIRES_NEW} transaction with bounded retry on
   * optimistic-lock and insertion races. A retry re-reads the profile and
   * recomputes from raw eligible history, so duplicate delivery cannot double
   * count.
   *
   * @param explicitlyVerified true only when a controlled VERIFY challenge was
   *     satisfied. This is the one case where an otherwise-outlying completed
   *     payment may enter the baseline.
   */
  public void updateProfileAfterOutcome(
      UUID userId,
      Transaction transaction,
      OffsetDateTime asOf,
      RecommendedAction recommendedAction,
      BehavioralOutcome outcome,
      boolean explicitlyVerified,
      String source) {
    if (userId == null || transaction == null || transaction.getId() == null || outcome == null) {
      return;
    }
    OffsetDateTime effectiveAsOf = asOf != null ? asOf : OffsetDateTime.now();
    String effectiveSource = source == null || source.isBlank() ? "UNKNOWN" : source;

    if (outcomeEvents == null) {
      applyOutcomeWithoutLedger(
          userId, transaction, effectiveAsOf, recommendedAction, outcome, explicitlyVerified);
      return;
    }

    int attempt = 0;
    while (true) {
      try {
        Runnable task =
            () ->
                applyOutcome(
                    userId,
                    transaction,
                    effectiveAsOf,
                    recommendedAction,
                    outcome,
                    explicitlyVerified,
                    effectiveSource);
        if (requiresNewTransaction != null) {
          requiresNewTransaction.executeWithoutResult(status -> task.run());
        } else {
          task.run();
        }
        return;
      } catch (RuntimeException failure) {
        if (failure instanceof DataIntegrityViolationException) {
          if (outcomeEvents.existsByTransactionIdAndOutcome(transaction.getId(), outcome)) {
            log.debug("Behavioral outcome already recorded (idempotent skip)");
            return;
          }
          if (attempt < MAX_RETRIES) {
            attempt++;
            pauseBeforeRetry(attempt, failure);
            continue;
          }
          log.warn(
              "Behavioral outcome could not be recorded after {} attempts",
              MAX_RETRIES + 1,
              failure);
          return;
        }
        if (isOptimisticLockFailure(failure)) {
          if (attempt < MAX_RETRIES) {
            attempt++;
            pauseBeforeRetry(attempt, failure);
            continue;
          }
          log.warn(
              "Behavioral profile update failed after {} retries; profile remains slightly stale",
              MAX_RETRIES,
              failure);
          return;
        }
        throw failure;
      }
    }
  }

  private void applyOutcome(
      UUID userId,
      Transaction transaction,
      OffsetDateTime asOf,
      RecommendedAction recommendedAction,
      BehavioralOutcome outcome,
      boolean explicitlyVerified,
      String source) {
    if (outcomeEvents.findByTransactionIdAndOutcome(transaction.getId(), outcome).isPresent()) {
      return;
    }

    boolean learnable =
        outcome == BehavioralOutcome.COMPLETED
            && determineLearnable(
                userId, transaction, asOf, recommendedAction, explicitlyVerified);

    BehavioralOutcomeEvent event = new BehavioralOutcomeEvent();
    event.setTransaction(transaction);
    event.setOutcome(outcome);
    event.setLearnable(learnable);
    event.setSource(source);
    event.setRecommendedAction(recommendedAction);
    event.setOccurredAt(asOf);
    outcomeEvents.saveAndFlush(event);

    if (outcome == BehavioralOutcome.COMPLETED) {
      recomputeProfile(userId, transaction, asOf);
    } else if (isExclusionOutcome(outcome) && profiles.existsByUserId(userId)) {
      // A refund/reversal/dispute removes a previously learned payment.
      recomputeProfile(userId, transaction, asOf);
    }
  }

  private void applyOutcomeWithoutLedger(
      UUID userId,
      Transaction transaction,
      OffsetDateTime asOf,
      RecommendedAction recommendedAction,
      BehavioralOutcome outcome,
      boolean explicitlyVerified) {
    if (outcome != BehavioralOutcome.COMPLETED) {
      return;
    }
    UserBehaviorProfile existing = profiles.findByUserId(userId).orElse(null);
    if (existing != null
        && transaction.getId().equals(existing.getLastIncludedTransactionId())) {
      return;
    }
    if (!determineLearnable(userId, transaction, asOf, recommendedAction, explicitlyVerified)) {
      return;
    }
    recomputeProfile(userId, transaction, asOf);
  }

  private boolean determineLearnable(
      UUID userId,
      Transaction transaction,
      OffsetDateTime asOf,
      RecommendedAction recommendedAction,
      boolean explicitlyVerified) {
    if (transaction.getStatus() != TransactionStatus.COMPLETED) {
      return false;
    }
    if (recommendedAction == null || recommendedAction == RecommendedAction.STOP) {
      return false;
    }
    if (recommendedAction == RecommendedAction.VERIFY && !explicitlyVerified) {
      return false;
    }
    if (outcomeEvents != null) {
      List<BehavioralOutcomeEvent> events = outcomeEvents.findByTransactionId(transaction.getId());
      boolean excluded =
          events.stream()
              .anyMatch(event -> !event.isLearnable() || isExclusionOutcome(event.getOutcome()));
      if (excluded) {
        return false;
      }
    }
    // Outliers are not learned on trust: only an explicit VERIFY challenge can
    // admit an abrupt amount change. Gradual changes stay within the threshold.
    return explicitlyVerified || !isPoisoningOutlier(userId, transaction, asOf);
  }

  private boolean isPoisoningOutlier(
      UUID userId, Transaction transaction, OffsetDateTime asOf) {
    if (transaction.getAmount() == null
        || transaction.getCurrency() == null
        || transaction.getCurrency().isBlank()) {
      return false;
    }
    List<Transaction> history =
        loadEligibleHistory(userId, asOf, transaction.getId(), excludedTransactionIds(userId));
    List<Transaction> currencyHistory =
        history.stream()
            .filter(t -> transaction.getCurrency().equalsIgnoreCase(t.getCurrency()))
            .toList();
    if (currencyHistory.size() < MIN_TRANSACTIONS_FOR_BASELINE) {
      return false;
    }
    AmountStats stats = computeAmountStats(currencyHistory);
    if (stats == null || stats.median() == null || stats.median().compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    if (stats.mad() != null && stats.mad().compareTo(ZERO_MAD_THRESHOLD) > 0) {
      BigDecimal modifiedZScore =
          transaction
              .getAmount()
              .subtract(stats.median())
              .abs()
              .multiply(new BigDecimal("0.6745"))
              .divide(stats.mad(), 4, RoundingMode.HALF_UP);
      return modifiedZScore.compareTo(POISONING_MODIFIED_Z_THRESHOLD) > 0;
    }
    BigDecimal ratio =
        transaction.getAmount().divide(stats.median(), 4, RoundingMode.HALF_UP);
    return ratio.compareTo(POISONING_RATIO_THRESHOLD) > 0
        || ratio.compareTo(BigDecimal.ONE.divide(
                POISONING_RATIO_THRESHOLD, 4, RoundingMode.HALF_UP))
            < 0;
  }

  private void recomputeProfile(
      UUID userId, Transaction transaction, OffsetDateTime asOf) {
    UserBehaviorProfile profile =
        profiles
            .findByUserId(userId)
            .orElseGet(
                () -> {
                  UserBehaviorProfile newProfile = new UserBehaviorProfile();
                  newProfile.setUser(entityManager.getReference(User.class, userId));
                  newProfile.setProfileVersion(1);
                  newProfile.setProfileStatus(UserBehaviorProfile.ProfileStatus.INSUFFICIENT_DATA);
                  newProfile.setTransactionCount(0);
                  newProfile.setLastUpdatedAt(asOf);
                  newProfile.setDataQualityScore(BigDecimal.ZERO);
                  newProfile.setCurrency(transaction.getCurrency());
                  return newProfile;
                });

    Set<UUID> excluded = excludedTransactionIds(userId);
    List<Transaction> history = loadEligibleHistory(userId, asOf, null, excluded);
    ProfileConfidence confidence = assessConfidence(history, userId);
    String currency =
        transaction.getCurrency() != null
            ? transaction.getCurrency()
            : profile.getCurrency();

    profile.setProfileVersion(profile.getProfileVersion() + 1);
    profile.setProfileStatus(confidence.status());
    profile.setTransactionCount(history.size());
    profile.setLastUpdatedAt(asOf);
    profile.setDataQualityScore(computeDataQuality(history.size(), confidence.status()));
    profile.setCurrency(currency);
    profile.setFirstObservedAt(
        history.stream()
            .map(Transaction::getTransactionTimestamp)
            .filter(java.util.Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null));

    List<Transaction> currencyHistory =
        history.stream()
            .filter(t -> currency == null || currency.equalsIgnoreCase(t.getCurrency()))
            .toList();
    AmountStats stats = computeAmountStats(currencyHistory);
    if (stats != null) {
      profile.setAmountMin(stats.min());
      profile.setAmountMax(stats.max());
      profile.setAmountMean(stats.mean());
      profile.setAmountMedian(stats.median());
      profile.setAmountStddev(stats.stddev());
      profile.setAmountMad(stats.mad());
      profile.setAmountP90(stats.p90());
      profile.setAmountP95(stats.p95());
    } else {
      profile.setAmountMin(null);
      profile.setAmountMax(null);
      profile.setAmountMean(null);
      profile.setAmountMedian(null);
      profile.setAmountStddev(null);
      profile.setAmountMad(null);
      profile.setAmountP90(null);
      profile.setAmountP95(null);
    }

    if (history.isEmpty()) {
      profile.setFrequencyPerDay(BigDecimal.ZERO);
      profile.setFrequencyPerWeek(BigDecimal.ZERO);
      profile.setFrequencyPerHour(BigDecimal.ZERO);
      profile.setAvgIntervalMinutes(null);
      profile.setPeakHour(null);
      profile.setNightPaymentRatio(BigDecimal.ZERO);
      profile.setWeekendPaymentRatio(BigDecimal.ZERO);
    } else {
      OffsetDateTime firstTx = history.get(0).getTransactionTimestamp();
      long daysSpan = Math.max(1, ChronoUnit.DAYS.between(firstTx, asOf) + 1);
      profile.setFrequencyPerDay(
          BigDecimal.valueOf((double) history.size() / daysSpan)
              .setScale(4, RoundingMode.HALF_UP));
      profile.setFrequencyPerWeek(
          profile
              .getFrequencyPerDay()
              .multiply(BigDecimal.valueOf(7))
              .setScale(4, RoundingMode.HALF_UP));
      long totalMinutes = ChronoUnit.MINUTES.between(firstTx, asOf);
      profile.setAvgIntervalMinutes(
          totalMinutes > 0 && history.size() > 1
              ? BigDecimal.valueOf((double) totalMinutes / (history.size() - 1))
                  .setScale(4, RoundingMode.HALF_UP)
              : null);

      Map<Integer, Long> hourCounts = new HashMap<>();
      for (Transaction t : history) {
        OffsetDateTime ts = t.getTransactionTimestamp();
        if (ts != null) {
          hourCounts.merge(ts.getHour(), 1L, Long::sum);
        }
      }
      profile.setPeakHour(
          hourCounts.entrySet().stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse(null));
      long nightCount =
          hourCounts.entrySet().stream()
              .filter(e -> e.getKey() >= 0 && e.getKey() <= 5)
              .mapToLong(Map.Entry::getValue)
              .sum();
      profile.setNightPaymentRatio(
          BigDecimal.valueOf((double) nightCount / history.size())
              .setScale(4, RoundingMode.HALF_UP));
      long weekendTx =
          history.stream()
              .filter(
                  t ->
                      t.getTransactionTimestamp() != null
                          && t.getTransactionTimestamp().getDayOfWeek().getValue() >= 6)
              .count();
      profile.setWeekendPaymentRatio(
          BigDecimal.valueOf((double) weekendTx / history.size())
              .setScale(4, RoundingMode.HALF_UP));
    }

    int uniqueRecipients =
        (int)
            history.stream()
                .map(Transaction::getRecipient)
                .filter(r -> r != null && r.getId() != null)
                .map(Recipient::getId)
                .distinct()
                .count();
    profile.setUniqueRecipientCount(uniqueRecipients);

    long establishedRecipientCount =
        history.stream()
            .map(Transaction::getRecipient)
            .filter(r -> r != null && r.getId() != null)
            .map(Recipient::getId)
            .distinct()
            .filter(
                rid ->
                    history.stream()
                            .filter(t -> t.getRecipient() != null && rid.equals(t.getRecipient().getId()))
                            .count()
                        >= 3)
            .count();
    profile.setEstablishedRecipientCount((int) establishedRecipientCount);
    profile.setNewRecipientRatio(
        uniqueRecipients > 0
            ? BigDecimal.valueOf(
                    (double) (uniqueRecipients - establishedRecipientCount) / uniqueRecipients)
                .setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO);

    int distinctDevices =
        (int)
            history.stream()
                .map(Transaction::getDeviceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
    profile.setDeviceCount(distinctDevices);

    int distinctCountries =
        (int)
            history.stream()
                .map(Transaction::getGeoCountry)
                .filter(java.util.Objects::nonNull)
                .map(String::toUpperCase)
                .distinct()
                .count();
    profile.setGeoCountryCount(distinctCountries);

    profile.setLastIncludedTransactionId(
        history.isEmpty()
            ? null
            : history.get(history.size() - 1).getId());

    profiles.saveAndFlush(profile);

    // Rebuild dimension tables from scratch so excluded/refunded payments do
    // not leave stale recipient/hour/geo/device counts behind.
    recipientBehaviors.deleteByUserId(userId);
    hourlyBehaviors.deleteByUserId(userId);
    geoBehaviors.deleteByUserId(userId);
    deviceBehaviors.deleteByUserId(userId);
    recipientBehaviors.flush();
    hourlyBehaviors.flush();
    geoBehaviors.flush();
    deviceBehaviors.flush();

    persistRecipientBehaviors(userId, history);
    persistHourlyBehaviors(userId, history);
    persistGeoBehaviors(userId, history);
    persistDeviceBehaviors(userId, history);
  }

  private static boolean isExclusionOutcome(BehavioralOutcome outcome) {
    return outcome == BehavioralOutcome.REFUNDED
        || outcome == BehavioralOutcome.REVERSED
        || outcome == BehavioralOutcome.DISPUTED;
  }

  private static BehavioralOutcome outcomeFor(TransactionStatus status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case COMPLETED -> BehavioralOutcome.COMPLETED;
      case FAILED -> BehavioralOutcome.FAILED;
      case CANCELLED -> BehavioralOutcome.CANCELLED;
      default -> null;
    };
  }

  private Set<UUID> excludedTransactionIds(UUID userId) {
    if (outcomeEvents == null) {
      return Set.of();
    }
    Set<UUID> excluded = new HashSet<>(outcomeEvents.findNonLearnableTransactionIds(userId));
    excluded.addAll(
        outcomeEvents.findTransactionIdsByOutcomeIn(
            userId,
            List.of(
                BehavioralOutcome.REFUNDED,
                BehavioralOutcome.REVERSED,
                BehavioralOutcome.DISPUTED)));
    return excluded;
  }

  private static boolean isOptimisticLockFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof ObjectOptimisticLockingFailureException
          || current instanceof StaleObjectStateException
          || current instanceof StaleStateException
          || current.getClass().getName().contains("OptimisticLock")) {
        return true;
      }
    }
    return false;
  }

  private static void pauseBeforeRetry(int attempt, RuntimeException failure) {
    try {
      Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      log.debug("Interrupted while waiting to retry behavioral profile update", failure);
    }
  }

  private AmountStats primaryAmountStats(List<Transaction> history) {
    if (history.isEmpty()) {
      return null;
    }
    return computeAmountStats(history);
  }

  private void persistRecipientBehaviors(UUID userId, List<Transaction> history) {
    // Group by recipient
    Map<UUID, List<Transaction>> byRecipient = new HashMap<>();
    for (Transaction t : history) {
      if (t.getRecipient() != null && t.getRecipient().getId() != null) {
        byRecipient.computeIfAbsent(t.getRecipient().getId(), k -> new ArrayList<>()).add(t);
      }
    }

    for (Map.Entry<UUID, List<Transaction>> entry : byRecipient.entrySet()) {
      UUID recipientId = entry.getKey();
      List<Transaction> txns = entry.getValue();

      UserRecipientBehavior behavior = recipientBehaviors
          .findByUserIdAndRecipientId(userId, recipientId)
          .orElseGet(() -> {
            UserRecipientBehavior b = new UserRecipientBehavior();
            b.setUser(entityManager.getReference(User.class, userId));
            b.setRecipient(entityManager.getReference(Recipient.class, recipientId));
            return b;
          });

      List<BigDecimal> amounts = txns.stream()
          .map(Transaction::getAmount)
          .sorted()
          .toList();
      BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
      behavior.setTransactionCount(txns.size());
      behavior.setTotalAmount(total);
      behavior.setAvgAmount(total.divide(BigDecimal.valueOf(txns.size()), 4, RoundingMode.HALF_UP));
      behavior.setMedianAmount(median(amounts));
      behavior.setMinAmount(amounts.isEmpty() ? null : amounts.get(0));
      behavior.setMaxAmount(amounts.isEmpty() ? null : amounts.get(amounts.size() - 1));
      behavior.setFirstSeenAt(txns.stream()
          .map(Transaction::getTransactionTimestamp)
          .min(Comparator.naturalOrder()).orElse(null));
      behavior.setLastSeenAt(txns.stream()
          .map(Transaction::getTransactionTimestamp)
          .max(Comparator.naturalOrder()).orElse(null));

      recipientBehaviors.saveAndFlush(behavior);
    }
  }

  private void persistHourlyBehaviors(UUID userId, List<Transaction> history) {
    Map<Integer, List<Transaction>> byHour = new HashMap<>();
    for (Transaction t : history) {
      OffsetDateTime ts = t.getTransactionTimestamp();
      if (ts != null) {
        byHour.computeIfAbsent(ts.getHour(), k -> new ArrayList<>()).add(t);
      }
    }

    for (int hour = 0; hour < 24; hour++) {
      final int hourOfDay = hour;
      List<Transaction> txns = byHour.getOrDefault(hour, List.of());
      if (txns.isEmpty()) continue;

      UserHourlyBehavior behavior = hourlyBehaviors
          .findByUserIdAndHourOfDay(userId, hourOfDay)
          .orElseGet(() -> {
            UserHourlyBehavior b = new UserHourlyBehavior();
            b.setUser(entityManager.getReference(User.class, userId));
            b.setHourOfDay(hourOfDay);
            return b;
          });

      behavior.setTransactionCount(txns.size());
      BigDecimal total = txns.stream()
          .map(Transaction::getAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      behavior.setTotalAmount(total);
      behavior.setLastUpdatedAt(OffsetDateTime.now());

      hourlyBehaviors.saveAndFlush(behavior);
    }
  }

  private void persistGeoBehaviors(UUID userId, List<Transaction> history) {
    Map<String, List<Transaction>> byCountry = new HashMap<>();
    for (Transaction t : history) {
      String country = t.getGeoCountry();
      if (country != null) {
        byCountry.computeIfAbsent(country.toUpperCase(), k -> new ArrayList<>()).add(t);
      }
    }

    for (Map.Entry<String, List<Transaction>> entry : byCountry.entrySet()) {
      String country = entry.getKey();
      List<Transaction> txns = entry.getValue();

      UserGeoBehavior behavior = geoBehaviors
          .findByUserIdAndGeoCountry(userId, country)
          .orElseGet(() -> {
            UserGeoBehavior b = new UserGeoBehavior();
            b.setUser(entityManager.getReference(User.class, userId));
            b.setGeoCountry(country);
            return b;
          });

      behavior.setTransactionCount(txns.size());
      BigDecimal total = txns.stream()
          .map(Transaction::getAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      behavior.setTotalAmount(total);
      behavior.setFirstSeenAt(txns.stream()
          .map(Transaction::getTransactionTimestamp)
          .min(Comparator.naturalOrder()).orElse(null));
      behavior.setLastSeenAt(txns.stream()
          .map(Transaction::getTransactionTimestamp)
          .max(Comparator.naturalOrder()).orElse(null));

      geoBehaviors.saveAndFlush(behavior);
    }
  }

  private void persistDeviceBehaviors(UUID userId, List<Transaction> history) {
    Map<String, List<Transaction>> byDevice = new HashMap<>();
    for (Transaction t : history) {
      UUID deviceId = t.getDeviceId();
      String fingerprint = t.getDeviceFingerprint();
      if (deviceId != null || fingerprint != null) {
        String key = (deviceId != null ? deviceId.toString() : "null") + "|" +
            (fingerprint != null ? fingerprint : "null");
        byDevice.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
      }
    }

    for (Map.Entry<String, List<Transaction>> entry : byDevice.entrySet()) {
      List<Transaction> txns = entry.getValue();
      Transaction first = txns.get(0);

      UserDeviceBehavior behavior = deviceBehaviors
          .findByUserIdAndDeviceIdAndDeviceFingerprint(
              userId, first.getDeviceId(), first.getDeviceFingerprint())
          .orElseGet(() -> {
            UserDeviceBehavior b = new UserDeviceBehavior();
            b.setUser(entityManager.getReference(User.class, userId));
            b.setDeviceId(first.getDeviceId());
            b.setDeviceFingerprint(first.getDeviceFingerprint());
            return b;
          });

      behavior.setTransactionCount(txns.size());
      behavior.setFirstSeenAt(txns.stream()
          .map(Transaction::getTransactionTimestamp)
          .min(Comparator.naturalOrder()).orElse(null));
      behavior.setLastSeenAt(txns.stream()
          .map(Transaction::getTransactionTimestamp)
          .max(Comparator.naturalOrder()).orElse(null));

      deviceBehaviors.saveAndFlush(behavior);
    }
  }

  private String userTimezoneFromProfile(
      Optional<UserBehaviorProfile> existingProfile) {
    return existingProfile.map(UserBehaviorProfile::getTimezone).orElse(null);
  }

  /**
   * Loads eligible historical transactions: COMPLETED only, before the
   * asOf timestamp, owner-scoped, bounded to avoid unbounded scans, and with
   * every non-learnable ledger outcome excluded.
   */
  private List<Transaction> loadEligibleHistory(
      UUID userId, OffsetDateTime asOf, UUID excludeTransactionId) {
    return loadEligibleHistory(
        userId, asOf, excludeTransactionId, excludedTransactionIds(userId));
  }

  private List<Transaction> loadEligibleHistory(
      UUID userId, OffsetDateTime asOf, UUID excludeTransactionId, Set<UUID> excludedTransactionIds) {
    if (userId == null || transactions == null) {
      return List.of();
    }
    return transactions.findByUserId(userId, PageRequest.of(0, 500))
        .getContent()
        .stream()
        .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
        .filter(t -> t.getTransactionTimestamp() != null)
        .filter(t -> t.getTransactionTimestamp().isBefore(asOf))
        .filter(t -> excludeTransactionId == null || !excludeTransactionId.equals(t.getId()))
        .filter(
            t ->
                excludedTransactionIds == null
                    || t.getId() == null
                    || !excludedTransactionIds.contains(t.getId()))
        .sorted(Comparator.comparing(Transaction::getTransactionTimestamp))
        .toList();
  }

  private ProfileConfidence assessConfidence(List<Transaction> history, UUID userId) {
    int count = history.size();
    UserBehaviorProfile.ProfileStatus status;
    boolean sufficientAmount, sufficientTime, sufficientRecipient, sufficientDevice,
        sufficientGeo, sufficientFrequency;
    String reason;

    if (count < MIN_TRANSACTIONS_FOR_BASELINE) {
      status = UserBehaviorProfile.ProfileStatus.INSUFFICIENT_DATA;
      sufficientAmount = sufficientTime = sufficientRecipient = sufficientDevice =
          sufficientGeo = sufficientFrequency = false;
      reason = "Fewer than " + MIN_TRANSACTIONS_FOR_BASELINE + " completed transactions";
    } else if (count < MIN_TRANSACTIONS_FOR_ESTABLISHED) {
      status = UserBehaviorProfile.ProfileStatus.INITIALIZING;
      sufficientAmount = true;
      sufficientTime = count >= 10;
      sufficientRecipient = count >= 10;
      sufficientDevice = count >= 10;
      sufficientGeo = count >= 10;
      sufficientFrequency = count >= 10;
      reason = "Initializing profile; limited history";
    } else {
      status = UserBehaviorProfile.ProfileStatus.ESTABLISHED;
      sufficientAmount = sufficientTime = sufficientRecipient = sufficientDevice =
          sufficientGeo = sufficientFrequency = true;
      reason = "Established behavioral baseline";
    }

    return new ProfileConfidence(
        status, count, sufficientAmount, sufficientTime, sufficientRecipient,
        sufficientDevice, sufficientGeo, sufficientFrequency, reason);
  }

  private BehavioralWindow selectPrimaryWindow(Map<String, AmountStats> statsByWindow) {
    // Prefer long-term if established; otherwise the largest window with
    // at least MIN_TRANSACTIONS_FOR_BASELINE points.
    for (BehavioralWindow w :
        new BehavioralWindow[] {BehavioralWindow.LONG_TERM, BehavioralWindow.MEDIUM_TERM,
            BehavioralWindow.SHORT_TERM}) {
      AmountStats s = statsByWindow.get(w.getLabel());
      if (s != null && s.count() >= MIN_TRANSACTIONS_FOR_BASELINE) {
        return w;
      }
    }
    return statsByWindow.entrySet().stream()
        .max(Map.Entry.comparingByValue(
            Comparator.comparingInt(AmountStats::count)))
        .map(Map.Entry::getKey)
        .map(label -> {
          for (BehavioralWindow w : BehavioralWindow.values()) {
            if (w.getLabel().equals(label)) return w;
          }
          return BehavioralWindow.SHORT_TERM;
        })
        .orElse(BehavioralWindow.SHORT_TERM);
  }

  private AmountStats computeAmountStats(List<Transaction> windowHistory) {
    if (windowHistory.isEmpty()) {
      return null;
    }
    List<BigDecimal> amounts =
        windowHistory.stream().map(Transaction::getAmount).sorted().toList();
    int n = amounts.size();
    BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);

    BigDecimal median = median(amounts);
    BigDecimal min = amounts.get(0);
    BigDecimal max = amounts.get(n - 1);

    List<BigDecimal> deviations =
        amounts.stream()
            .map(a -> a.subtract(median).abs())
            .sorted()
            .toList();
    BigDecimal mad = median(deviations);

    BigDecimal varianceSum =
        amounts.stream()
            .map(a -> a.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(Math.max(1, n - 1)), 6, RoundingMode.HALF_UP);
    BigDecimal stddev = sqrt(variance);

    BigDecimal p90 = percentile(amounts, 0.90);
    BigDecimal p95 = percentile(amounts, 0.95);

    return new AmountStats(n, min, max, mean, median, stddev, mad, p90, p95);
  }

  private BehavioralFindingDto analyzeAmountDeviation(
      BigDecimal currentAmount,
      AmountStats stats,
      String window,
      String profileStatus,
      String currency) {
    if (currentAmount == null || stats.count() < MIN_TRANSACTIONS_FOR_BASELINE) {
      return null;
    }

    BigDecimal median = stats.median();
    BigDecimal mad = stats.mad();

    BigDecimal deviationRatio;
    String severity;
    String evidence;
    Double confidence = computeConfidence(stats.count());

    if (mad != null && mad.compareTo(ZERO_MAD_THRESHOLD) > 0) {
      BigDecimal modifiedZScore =
          currentAmount.subtract(median).abs().multiply(new BigDecimal("0.6745"))
              .divide(mad, 4, RoundingMode.HALF_UP);
      deviationRatio = currentAmount.divide(median, 4, RoundingMode.HALF_UP);

      if (modifiedZScore.compareTo(new BigDecimal("3.5")) > 0) {
        severity = BehavioralSeverity.HIGH.name();
        evidence = "Amount " + currentAmount + " " + currency
            + " deviates " + modifiedZScore
            + " modified Z-scores from your median of " + median + " " + currency + ".";
      } else if (modifiedZScore.compareTo(new BigDecimal("2.5")) > 0) {
        severity = BehavioralSeverity.MEDIUM.name();
        evidence = "Amount " + currentAmount + " " + currency
            + " is moderately above your typical pattern (median " + median + " " + currency + ").";
      } else {
        return null;
      }
    } else {
      // Zero MAD: all historical amounts identical
      if (currentAmount.compareTo(median) != 0) {
        deviationRatio = median.compareTo(BigDecimal.ZERO) > 0
            ? currentAmount.divide(median, 4, RoundingMode.HALF_UP)
            : BigDecimal.ONE;
        // Only flag if deviation is meaningful
        if (deviationRatio.compareTo(MIN_MEANINGFUL_DEVIATION_RATIO) < 0
            && deviationRatio.compareTo(BigDecimal.ONE.divide(MIN_MEANINGFUL_DEVIATION_RATIO, 4, RoundingMode.HALF_UP)) > 0) {
          return null; // Variation within noise
        }
        severity = BehavioralSeverity.MEDIUM.name();
        evidence = "Amount " + currentAmount + " " + currency
            + " differs from your consistent historical amount of " + median + " " + currency + ".";
      } else {
        return null;
      }
    }

    return new BehavioralFindingDto(
        "AMOUNT_DEVIATION",
        "Unusual payment amount",
        evidence,
        severity,
        confidence,
        currentAmount,
        median,
        deviationRatio,
        window,
        profileStatus,
        evidence,
        List.of("Personalized amount distribution (" + currency + ", MAD-based)"),
        OffsetDateTime.now());
  }
  private BehavioralFindingDto analyzeTimeDeviation(
      OffsetDateTime transactionTime,
      List<Transaction> history,
      OffsetDateTime asOf,
      String profileStatus,
      String userTimezone,
      String profileWindow) {
    ZoneId zone = parseTimezone(userTimezone);
    int hour = transactionTime.atZoneSameInstant(zone).getHour();

    Map<Integer, Long> hourCounts = new HashMap<>();
    for (Transaction t : history) {
      OffsetDateTime ts = t.getTransactionTimestamp();
      if (ts != null) {
        int h = ts.atZoneSameInstant(zone).getHour();
        hourCounts.merge(h, 1L, Long::sum);
      }
    }

    long total = history.size();
    if (total < MIN_TRANSACTIONS_FOR_BASELINE) {
      return null;
    }

    long countAtHour = hourCounts.getOrDefault(hour, 0L);
    double hourRatio = (double) countAtHour / total;

    boolean isNight = hour >= 0 && hour <= 5;
    long nightCount = hourCounts.entrySet().stream()
        .filter(e -> e.getKey() >= 0 && e.getKey() <= 5)
        .mapToLong(Map.Entry::getValue)
        .sum();
    double nightRatio = total > 0 ? (double) nightCount / total : 0.0;

    String severity;
    String evidence;

    if (hourRatio < 0.05 && total >= MIN_TRANSACTIONS_FOR_ESTABLISHED) {
      severity = isNight ? BehavioralSeverity.HIGH.name() : BehavioralSeverity.MEDIUM.name();
      evidence =
          "Payment at " + String.format("%02d:00", hour)
              + " is outside your usual activity hours ("
              + String.format("%.0f%%", hourRatio * 100)
              + " of your historical payments occur at this hour).";
    } else if (isNight && nightRatio < 0.10) {
      severity = BehavioralSeverity.MEDIUM.name();
      evidence =
          "Late-night payment (" + String.format("%02d:00", hour)
              + ") is unusual for your account ("
              + String.format("%.0f%%", nightRatio * 100)
              + " of payments occur at night).";
    } else {
      return null;
    }

    return new BehavioralFindingDto(
        "UNUSUAL_PAYMENT_HOUR",
        "Unusual payment time",
        evidence,
        severity,
        computeConfidence((int) total),
        BigDecimal.valueOf(hour),
        BigDecimal.valueOf(Math.round(hourRatio * 100)),
        null,
        profileWindow,
        profileStatus,
        evidence,
        List.of("Personalized hour-of-day distribution (timezone: " + zone + ")"),
        OffsetDateTime.now());
  }
  private ZoneId parseTimezone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return ZoneId.of("UTC");
    }
    try {
      return ZoneId.of(timezone);
    } catch (Exception e) {
      return ZoneId.of("UTC");
    }
  }
  private BehavioralFindingDto analyzeRecipientDeviation(
      UUID userId,
      UUID recipientId,
      BigDecimal amount,
      List<Transaction> history,
      OffsetDateTime asOf,
      String profileStatus,
      String profileWindow) {
    long recipientTxCount =
        history.stream()
            .filter(t -> t.getRecipient() != null && recipientId.equals(t.getRecipient().getId()))
            .count();

    int uniqueRecipients =
        (int) history.stream()
            .map(Transaction::getRecipient)
            .filter(java.util.Objects::nonNull)
            .map(r -> r.getId())
            .distinct()
            .count();

    if (uniqueRecipients < MIN_TRANSACTIONS_FOR_BASELINE) {
      return null;
    }

    String severity;
    String evidence;

    if (recipientTxCount == 0) {
      severity = BehavioralSeverity.MEDIUM.name();
      evidence =
          "This recipient has not appeared in your previous payment history "
              + "(" + uniqueRecipients + " established recipients total).";
    } else if (recipientTxCount < 3) {
      severity = BehavioralSeverity.LOW.name();
      evidence = "You have only paid this recipient " + recipientTxCount
          + " time(s) previously; not an established payee.";
    } else {
      List<BigDecimal> recipientAmounts =
          history.stream()
              .filter(t -> t.getRecipient() != null && recipientId.equals(t.getRecipient().getId()))
              .map(Transaction::getAmount)
              .sorted()
              .toList();
      BigDecimal recipientMedian = median(recipientAmounts);
      if (amount != null
          && recipientMedian != null
          && amount.compareTo(recipientMedian.multiply(new BigDecimal("5"))) > 0) {
        severity = BehavioralSeverity.HIGH.name();
        evidence = "Amount " + amount
            + " is 5x+ your median payment to this recipient (" + recipientMedian + ").";
      } else {
        return null;
      }
    }

    return new BehavioralFindingDto(
        "RECIPIENT_BEHAVIOR_DEVIATION",
        "Unusual recipient pattern",
        evidence,
        severity,
        computeConfidence(history.size()),
        amount,
        null,
        recipientTxCount == 0 ? null : BigDecimal.valueOf(recipientTxCount),
        profileWindow,
        profileStatus,
        evidence,
        List.of("Personalized recipient history"),
        OffsetDateTime.now());
  }

  private BehavioralFindingDto analyzeFrequencyAnomaly(
      List<Transaction> history, OffsetDateTime asOf, String profileStatus) {
    if (history.size() < MIN_TRANSACTIONS_FOR_BASELINE) {
      return null;
    }

    OffsetDateTime dayStart = asOf.minus(1, ChronoUnit.DAYS);
    long recentCount =
        history.stream()
            .filter(t -> t.getTransactionTimestamp().isAfter(dayStart))
            .count();

    OffsetDateTime firstTx = history.get(0).getTransactionTimestamp();
    long daysSpan = Math.max(1, ChronoUnit.DAYS.between(firstTx, asOf) + 1);
    double historicalPerDay = (double) history.size() / daysSpan;

    double frequencyRatio = historicalPerDay > 0 ? recentCount / historicalPerDay : 0.0;

    String severity;
    String evidence;

    if (recentCount >= 10 && frequencyRatio >= 3.0) {
      severity = BehavioralSeverity.HIGH.name();
      evidence = "Sudden payment burst: " + recentCount
          + " payments in the last 24 hours vs your average of "
          + String.format("%.1f", historicalPerDay) + " per day.";
    } else if (recentCount >= 5 && frequencyRatio >= 2.0) {
      severity = BehavioralSeverity.MEDIUM.name();
      evidence = "Elevated payment frequency: " + recentCount
          + " payments in the last 24 hours vs your average of "
          + String.format("%.1f", historicalPerDay) + " per day.";
    } else {
      return null;
    }

    return new BehavioralFindingDto(
        "TRANSACTION_FREQUENCY_ANOMALY",
        "Unusual payment frequency",
        evidence,
        severity,
        computeConfidence(history.size()),
        BigDecimal.valueOf(recentCount),
        BigDecimal.valueOf(Math.round(historicalPerDay * 10) / 10.0),
        BigDecimal.valueOf(frequencyRatio),
        "7_DAYS",
        profileStatus,
        evidence,
        List.of("Personalized payment frequency distribution"),
        OffsetDateTime.now());
  }

  private BehavioralFindingDto analyzeDeviceDeviation(
      UUID userId,
      UUID deviceId,
      String deviceFingerprint,
      List<Transaction> history,
      String profileStatus,
      String profileWindow) {
    boolean deviceSeenById =
        deviceId != null
            && history.stream()
            .anyMatch(t -> t.getDeviceId() != null && deviceId.equals(t.getDeviceId()));
    boolean deviceSeenByFingerprint =
        deviceFingerprint != null
            && history.stream()
            .anyMatch(t -> t.getDeviceFingerprint() != null
                && t.getDeviceFingerprint().equals(deviceFingerprint));

    int distinctDevices =
        (int) history.stream()
            .map(Transaction::getDeviceId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count();

    if (distinctDevices < 2) {
      return null;
    }

    String severity;
    String evidence;

    if (!deviceSeenById && !deviceSeenByFingerprint) {
      severity = BehavioralSeverity.MEDIUM.name();
      evidence = "This device has not been used for your account before ("
          + distinctDevices + " known devices historically).";
    } else {
      return null;
    }

    return new BehavioralFindingDto(
        "DEVICE_BEHAVIOR_DEVIATION",
        "Unrecognized device",
        evidence,
        severity,
        computeConfidence(history.size()),
        null,
        null,
        BigDecimal.valueOf(distinctDevices),
        profileWindow,
        profileStatus,
        evidence,
        List.of("Personalized device history"),
        OffsetDateTime.now());
  }

  private BehavioralFindingDto analyzeGeoDeviation(
      UUID userId,
      String geoCountry,
      List<Transaction> history,
      OffsetDateTime asOf,
      String profileStatus,
      String profileWindow) {
    boolean countrySeen =
        history.stream()
            .anyMatch(t -> t.getGeoCountry() != null
                && t.getGeoCountry().equalsIgnoreCase(geoCountry));

    int distinctCountries =
        (int) history.stream()
            .map(Transaction::getGeoCountry)
            .filter(java.util.Objects::nonNull)
            .map(String::toUpperCase)
            .distinct()
            .count();

    if (distinctCountries < 2) {
      return null;
    }

    String severity;
    String evidence;

    if (!countrySeen) {
      severity = BehavioralSeverity.MEDIUM.name();
      evidence = "Payment from " + geoCountry + " is a new location for your account ("
          + distinctCountries + " countries historically).";
    } else {
      return null;
    }

    return new BehavioralFindingDto(
        "GEOGRAPHIC_BEHAVIOR_DEVIATION",
        "Unusual payment location",
        evidence,
        severity,
        computeConfidence(history.size()),
        null,
        null,
        BigDecimal.valueOf(distinctCountries),
        profileWindow,
        profileStatus,
        evidence,
        List.of("Personalized geographic history"),
        OffsetDateTime.now());
  }

  private String computeOverallDeviation(List<BehavioralFindingDto> findings) {
    // Align the aggregate with the analyzer's per-signal confidence gate. A
    // low-confidence finding is not emitted as a signal, so it must not inflate
    // the aggregate deviation either.
    List<BehavioralFindingDto> reliable =
        findings.stream()
            .filter(f -> f.confidence() != null && f.confidence() >= 0.6)
            .toList();
    if (reliable.isEmpty()) {
      return "NORMAL";
    }
    long highOrCritical =
        reliable.stream()
            .filter(f -> f.severity() != null
                && (f.severity().equals("HIGH") || f.severity().equals("CRITICAL")))
            .count();
    if (highOrCritical >= 2) {
      return "SIGNIFICANT";
    }
    if (highOrCritical == 1 || reliable.size() >= 3) {
      return "MODERATE";
    }
    return "MINOR";
  }

  private Double computeConfidence(int sampleSize) {
    if (sampleSize >= MIN_TRANSACTIONS_FOR_ESTABLISHED) {
      return 0.9;
    }
    if (sampleSize >= MIN_TRANSACTIONS_FOR_BASELINE) {
      return 0.7;
    }
    return 0.5;
  }

  private BigDecimal computeDataQuality(int sampleSize, UserBehaviorProfile.ProfileStatus status) {
    if (sampleSize >= MIN_TRANSACTIONS_FOR_ESTABLISHED) {
      return BigDecimal.valueOf(0.9);
    }
    if (sampleSize >= MIN_TRANSACTIONS_FOR_BASELINE) {
      return BigDecimal.valueOf(0.7);
    }
    return BigDecimal.valueOf(0.3);
  }

  private static BigDecimal median(List<BigDecimal> sorted) {
    int n = sorted.size();
    if (n == 0) {
      return null;
    }
    if (n % 2 == 0) {
      return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
    return sorted.get(n / 2);
  }

  private static BigDecimal percentile(List<BigDecimal> sorted, double p) {
    int n = sorted.size();
    if (n == 0) {
      return null;
    }
    double rank = p * (n - 1);
    int lower = (int) Math.floor(rank);
    int upper = (int) Math.ceil(rank);
    if (lower == upper) {
      return sorted.get(lower);
    }
    BigDecimal fraction = BigDecimal.valueOf(rank - lower);
    return sorted.get(lower)
        .add(sorted.get(upper).subtract(sorted.get(lower)).multiply(fraction));
  }

  private static BigDecimal sqrt(BigDecimal value) {
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal x0 = value.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    BigDecimal x1 =
        value.divide(x0, 4, RoundingMode.HALF_UP).add(x0).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    while (x1.subtract(x0).abs().compareTo(new BigDecimal("0.0001")) > 0) {
      x0 = x1;
      x1 = value.divide(x0, 4, RoundingMode.HALF_UP).add(x0).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
    return x1;
  }

  /** Amount statistics for one rolling window. */
  private record AmountStats(
      int count,
      BigDecimal min,
      BigDecimal max,
      BigDecimal mean,
      BigDecimal median,
      BigDecimal stddev,
      BigDecimal mad,
      BigDecimal p90,
      BigDecimal p95) {}
}
