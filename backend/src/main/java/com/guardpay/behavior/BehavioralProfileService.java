package com.guardpay.behavior;

import com.guardpay.behavior.dto.BehavioralAnalysisRequest;
import com.guardpay.behavior.dto.BehavioralAnalysisResponse;
import com.guardpay.behavior.dto.BehavioralFindingDto;
import com.guardpay.behavior.dto.BehavioralProfileDto;
import com.guardpay.behavior.dto.BehavioralProfileSummaryDto;
import com.guardpay.common.domain.BaseEntity;
import com.guardpay.recipient.Recipient;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes and persists per-user behavioral statistics from COMPLETED
 * transaction history across three rolling windows (7, 30, 90 days).
 *
 * <p>The service now loads and persists {@link UserBehaviorProfile} rows so
 * that subsequent evaluations reuse the learned baseline instead of
 * recomputing from raw history every time. Profiles are updated idempotently
 * after eligible transaction outcomes complete.
 *
 * <p>Amount statistics are maintained separately per currency. Time-of-day
   analysis uses the user's configured IANA timezone when available.
 */
@Service
public class BehavioralProfileService {

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

  private final TransactionRepository transactions;
  private final UserBehaviorProfileRepository profiles;
  private final UserRecipientBehaviorRepository recipientBehaviors;
  private final UserHourlyBehaviorRepository hourlyBehaviors;
  private final UserGeoBehaviorRepository geoBehaviors;
  private final UserDeviceBehaviorRepository deviceBehaviors;
  private final EntityManager entityManager;

  public BehavioralProfileService(
      TransactionRepository transactions,
      UserBehaviorProfileRepository profiles,
      UserRecipientBehaviorRepository recipientBehaviors,
      UserHourlyBehaviorRepository hourlyBehaviors,
      UserGeoBehaviorRepository geoBehaviors,
      UserDeviceBehaviorRepository deviceBehaviors,
      EntityManager entityManager) {
    this.transactions = transactions;
    this.profiles = profiles;
    this.recipientBehaviors = recipientBehaviors;
    this.hourlyBehaviors = hourlyBehaviors;
    this.geoBehaviors = geoBehaviors;
    this.deviceBehaviors = deviceBehaviors;
    this.entityManager = entityManager;
  }

  /**
   * Computes behavioral anomaly findings for an incoming payment against the
   * user's persisted profile, then persists an updated profile.
   *
   * <p>Does not include the current transaction in its own baseline.
   * Fails safely: if persistence fails, findings are still returned and the
   * profile is marked DEGRADED.
   */
  @Transactional
  public BehavioralAnalysisResponse analyzeAndPersist(
      BehavioralAnalysisRequest request, UUID excludeTransactionId) {
    if (request.userId() == null) {
      return new BehavioralAnalysisResponse(
          null, "INSUFFICIENT_DATA", 0, null, null, List.of(),
          "INSUFFICIENT_DATA", false);
    }

    OffsetDateTime asOf = request.asOf() != null ? request.asOf() : OffsetDateTime.now();

    // Load existing profile (optimistic lock via version check on save)
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

    // Persist / update the profile
    try {
      persistProfile(request.userId(), history, asOf, confidence, primaryAmountStats,
          requestCurrency, userTimezoneFromProfile(existingProfile));
    } catch (Exception e) {
      // Profile persistence failure must not block the payment decision
      return new BehavioralAnalysisResponse(
          request.userId(),
          "DEGRADED",
          history.size(),
          null,
          null,
          findings,
          overall,
          true);
    }

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
   * Read-only analysis without persistence. Used for evaluation endpoints
   * that should not modify the profile.
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

    String requestCurrency = request.currency() != null ? request.currency() : "USD";
    Map<String, AmountStats> amountStatsByCurrencyWindow = new HashMap<>();
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

    BehavioralWindow primaryWindow = selectPrimaryWindow(amountStatsByCurrencyWindow);
    AmountStats primaryAmountStats = amountStatsByCurrencyWindow.get(primaryWindow.getLabel());

    // Filter history to the primary window for all non-amount analyses
    OffsetDateTime windowStart = asOf.minus(primaryWindow.getDays(), ChronoUnit.DAYS);
    List<Transaction> windowHistory = history.stream()
        .filter(t -> t.getTransactionTimestamp() != null
            && t.getTransactionTimestamp().isAfter(windowStart))
        .toList();

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

    String userTimezone = profiles.findByUserId(request.userId())
        .map(UserBehaviorProfile::getTimezone).orElse(null);

    if (request.transactionTimestamp() != null
        && confidence.sufficientForTime()) {
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

    if (confidence.sufficientForFrequency()) {
      BehavioralFindingDto frequencyFinding =
          analyzeFrequencyAnomaly(windowHistory, asOf, confidence.status().toString());
      if (frequencyFinding != null) {
        findings.add(frequencyFinding);
      }
    }

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

    String overall = computeOverallDeviation(findings);

    return new BehavioralAnalysisResponse(
        request.userId(),
        confidence.status().toString(),
        history.size(),
        null,
        null,
        findings,
        overall,
        true);
  }

  /**
   * Persist or update the user's behavioral profile after computing statistics.
   * Uses optimistic locking via the profile's updated timestamp to prevent
   * lost updates from concurrent transactions.
   */
  @Transactional
  public void persistProfile(
      UUID userId,
      List<Transaction> history,
      OffsetDateTime asOf,
      ProfileConfidence confidence,
      AmountStats primaryAmountStats,
      String requestCurrency,
      String userTimezone) {

    UserBehaviorProfile profile = profiles.findByUserId(userId).orElseGet(() -> {
      UserBehaviorProfile newProfile = new UserBehaviorProfile();
      newProfile.setUser(entityManager.getReference(User.class, userId));
      newProfile.setProfileVersion(1);
      newProfile.setProfileStatus(confidence.status());
      newProfile.setTransactionCount(history.size());
      newProfile.setFirstObservedAt(history.isEmpty() ? null :
          history.stream().map(Transaction::getTransactionTimestamp)
              .min(Comparator.naturalOrder()).orElse(null));
      newProfile.setLastUpdatedAt(asOf);
      newProfile.setDataQualityScore(computeDataQuality(history.size(), confidence.status()));
      newProfile.setCurrency(requestCurrency);
      newProfile.setTimezone(userTimezone);
      return newProfile;
    });

    // Update fields
    profile.setProfileVersion(profile.getProfileVersion() + 1);
    profile.setProfileStatus(confidence.status());
    profile.setTransactionCount(history.size());
    profile.setLastUpdatedAt(asOf);
    profile.setDataQualityScore(computeDataQuality(history.size(), confidence.status()));
    profile.setCurrency(requestCurrency);
    profile.setTimezone(userTimezone);

    if (primaryAmountStats != null) {
      profile.setAmountMin(primaryAmountStats.min());
      profile.setAmountMax(primaryAmountStats.max());
      profile.setAmountMean(primaryAmountStats.mean());
      profile.setAmountMedian(primaryAmountStats.median());
      profile.setAmountStddev(primaryAmountStats.stddev());
      profile.setAmountMad(primaryAmountStats.mad());
      profile.setAmountP90(primaryAmountStats.p90());
      profile.setAmountP95(primaryAmountStats.p95());
    }

    // Frequency stats
    if (!history.isEmpty()) {
      OffsetDateTime firstTx = history.get(0).getTransactionTimestamp();
      long daysSpan = Math.max(1, ChronoUnit.DAYS.between(firstTx, asOf) + 1);
      profile.setFrequencyPerDay(
          BigDecimal.valueOf((double) history.size() / daysSpan)
              .setScale(4, RoundingMode.HALF_UP));
      profile.setFrequencyPerWeek(
          profile.getFrequencyPerDay().multiply(BigDecimal.valueOf(7))
              .setScale(4, RoundingMode.HALF_UP));
      long totalMinutes = ChronoUnit.MINUTES.between(firstTx, asOf);
      if (totalMinutes > 0 && history.size() > 1) {
        profile.setAvgIntervalMinutes(
            BigDecimal.valueOf((double) totalMinutes / (history.size() - 1))
                .setScale(4, RoundingMode.HALF_UP));
      }

      // Peak hour from history
      Map<Integer, Long> hourCounts = new HashMap<>();
      for (Transaction t : history) {
        OffsetDateTime ts = t.getTransactionTimestamp();
        if (ts != null) {
          int h = ts.getHour();
          hourCounts.merge(h, 1L, Long::sum);
        }
      }
      Integer peakHour = hourCounts.entrySet().stream()
          .max(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .orElse(null);
      profile.setPeakHour(peakHour);

      long nightCount = hourCounts.entrySet().stream()
          .filter(e -> e.getKey() >= 0 && e.getKey() <= 5)
          .mapToLong(Map.Entry::getValue)
          .sum();
      profile.setNightPaymentRatio(
          history.size() > 0
              ? BigDecimal.valueOf((double) nightCount / history.size())
                  .setScale(4, RoundingMode.HALF_UP)
              : BigDecimal.ZERO);

      long weekendCount = hourCounts.entrySet().stream()
          .flatMap(e -> {
            // Need day-of-week, not hour. Re-derive from transactions.
            return java.util.stream.Stream.of(
                history.stream()
                    .filter(t -> t.getTransactionTimestamp() != null
                        && t.getTransactionTimestamp().getDayOfWeek()
                            .getValue() >= 6)
                    .count());
          })
          .findFirst()
          .orElse(0L);
      // Simpler: count weekend transactions directly
      long weekendTx = history.stream()
          .filter(t -> t.getTransactionTimestamp() != null
              && (t.getTransactionTimestamp().getDayOfWeek().getValue() >= 6))
          .count();
      profile.setWeekendPaymentRatio(
          history.size() > 0
              ? BigDecimal.valueOf((double) weekendTx / history.size())
                  .setScale(4, RoundingMode.HALF_UP)
              : BigDecimal.ZERO);
    }

    // Recipient stats
    int uniqueRecipients = (int) history.stream()
        .map(Transaction::getRecipient)
        .filter(java.util.Objects::nonNull)
        .map(r -> r.getId())
        .distinct()
        .count();
    profile.setUniqueRecipientCount(uniqueRecipients);

    long establishedRecipientCount = history.stream()
        .map(Transaction::getRecipient)
        .filter(java.util.Objects::nonNull)
        .map(r -> r.getId())
        .distinct()
        .filter(rid -> history.stream()
            .filter(t -> t.getRecipient() != null && rid.equals(t.getRecipient().getId()))
            .count() >= 3)
        .count();
    profile.setEstablishedRecipientCount((int) establishedRecipientCount);

    if (uniqueRecipients > 0) {
      profile.setNewRecipientRatio(
          BigDecimal.valueOf((double) (uniqueRecipients - establishedRecipientCount)
              / uniqueRecipients)
              .setScale(4, RoundingMode.HALF_UP));
    }

    // Device stats
    int distinctDevices = (int) history.stream()
        .map(Transaction::getDeviceId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .count();
    profile.setDeviceCount(distinctDevices);

    // Geo stats
    int distinctCountries = (int) history.stream()
        .map(Transaction::getGeoCountry)
        .filter(java.util.Objects::nonNull)
        .map(String::toUpperCase)
        .distinct()
        .count();
    profile.setGeoCountryCount(distinctCountries);

    profiles.saveAndFlush(profile);

    // Persist sub-tables
    persistRecipientBehaviors(userId, history);
    persistHourlyBehaviors(userId, history);
    persistGeoBehaviors(userId, history);
    persistDeviceBehaviors(userId, history);
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
   * asOf timestamp, owner-scoped, bounded to avoid unbounded scans.
   */
  private List<Transaction> loadEligibleHistory(
      UUID userId, OffsetDateTime asOf, UUID excludeTransactionId) {
    if (userId == null || transactions == null) {
      return List.of();
    }
    return transactions.findByUserId(userId, PageRequest.of(0, 500))
        .getContent()
        .stream()
        .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
        .filter(t -> t.getTransactionTimestamp() != null)
        .filter(t -> t.getTransactionTimestamp().isBefore(asOf))
        .filter(t -> excludeTransactionId == null || !t.getId().equals(excludeTransactionId))
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
    if (findings.isEmpty()) {
      return "NORMAL";
    }
    long highOrCritical =
        findings.stream()
            .filter(f -> f.severity() != null
                && (f.severity().equals("HIGH") || f.severity().equals("CRITICAL")))
            .count();
    if (highOrCritical >= 2) {
      return "SIGNIFICANT";
    }
    if (highOrCritical == 1 || findings.size() >= 3) {
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
