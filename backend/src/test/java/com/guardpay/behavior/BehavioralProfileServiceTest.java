package com.guardpay.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.behavior.dto.BehavioralAnalysisRequest;
import com.guardpay.behavior.dto.BehavioralAnalysisResponse;
import com.guardpay.behavior.dto.BehavioralFindingDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.EntityManager;

/**
 * Unit tests for {@link BehavioralProfileService}. Tests cold-start handling
 * and basic behavioral analysis with mocked transaction history.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BehavioralProfileServiceTest {

  @Mock private TransactionRepository transactions;
  @Mock private UserBehaviorProfileRepository profiles;
  @Mock private UserRecipientBehaviorRepository recipientBehaviors;
  @Mock private UserHourlyBehaviorRepository hourlyBehaviors;
  @Mock private UserGeoBehaviorRepository geoBehaviors;
  @Mock private UserDeviceBehaviorRepository deviceBehaviors;
  @Mock private EntityManager entityManager;

  private BehavioralProfileService service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    service = new BehavioralProfileService(
        transactions, profiles, recipientBehaviors, hourlyBehaviors,
        geoBehaviors, deviceBehaviors, entityManager);
    // Default: no existing profile
    org.mockito.Mockito.lenient()
        .when(profiles.findByUserId(org.mockito.ArgumentMatchers.any(UUID.class)))
        .thenReturn(java.util.Optional.empty());
  }

  @Test
  void noHistoryReturnsInsufficientData() {
    mockEmptyHistory();
    var request = newRequest(new BigDecimal("1000"), null);

    var response = service.analyze(request, null);

    assertEquals("INSUFFICIENT_DATA", response.profileStatus());
    assertTrue(response.findings().isEmpty());
    assertFalse(response.available());
  }

  @Test
  void newUserWithLargeAmountNotFlaggedAsFraud() {
    mockEmptyHistory();
    var request = newRequest(new BigDecimal("99999"), null);

    var response = service.analyze(request, null);

    assertEquals("INSUFFICIENT_DATA", response.profileStatus());
    assertTrue(response.findings().isEmpty());
    assertFalse(response.available());
  }

  @Test
  void withSomeHistoryReturnsInitializingNotInsufficient() {
    List<Transaction> history = completedHistory(5, "1000");
    org.mockito.Mockito.doReturn(new PageImpl<>(history))
        .when(transactions)
        .findByUserId(org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.any(Pageable.class));

    var request = newRequest(new BigDecimal("1000"), OffsetDateTime.parse("2026-01-06T10:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("INITIALIZING", response.profileStatus());
    assertNotNull(response);
  }

  @Test
  void withEnoughHistoryReturnsEstablished() {
    List<Transaction> history = completedHistory(25, "1000");
    org.mockito.Mockito.doReturn(new PageImpl<>(history))
        .when(transactions)
        .findByUserId(org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.any(Pageable.class));

    var request = newRequest(new BigDecimal("1000"), OffsetDateTime.parse("2026-01-26T10:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
  }

  @Test
  void zeroMadIdenticalAmountsNoAnomalyForSameAmount() {
    List<Transaction> history = completedHistory(25, "1000");
    org.mockito.Mockito.doReturn(new PageImpl<>(history))
        .when(transactions)
        .findByUserId(org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.any(Pageable.class));

    // Same amount as historical - should not be anomalous
    var request = newRequest(new BigDecimal("1000"), OffsetDateTime.parse("2026-01-26T10:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
    // No amount deviation for same amount
    assertTrue(
        response.findings().stream().noneMatch(f -> "AMOUNT_DEVIATION".equals(f.code())));
  }

  @Test
  void unusualHourTriggersTimeFinding() {
    // 25 transactions all during business hours (9am-5pm)
    List<Transaction> history = completedHistoryAtHour(25, 10);
    org.mockito.Mockito.doReturn(new PageImpl<>(history))
        .when(transactions)
        .findByUserId(org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.any(Pageable.class));

    // Payment at 3 AM
    var request =
        newRequest(
            new BigDecimal("1000"), OffsetDateTime.parse("2026-01-26T03:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
    boolean hasTimeFinding =
        response.findings().stream().anyMatch(f -> "UNUSUAL_PAYMENT_HOUR".equals(f.code()));
    assertTrue(hasTimeFinding, "Expected UNUSUAL_PAYMENT_HOUR for 3 AM payment");
  }

  @Test
  void nullUserIdReturnsInsufficientData() {
    mockEmptyHistory();
    var request = newRequest(null, new BigDecimal("1000"), null);

    var response = service.analyze(request, null);

    assertEquals("INSUFFICIENT_DATA", response.profileStatus());
    assertTrue(response.findings().isEmpty());
  }

  private com.guardpay.behavior.dto.BehavioralAnalysisRequest newRequest(
      BigDecimal amount, OffsetDateTime timestamp) {
    return new com.guardpay.behavior.dto.BehavioralAnalysisRequest(
        userId, amount, "USD", timestamp, null, null, null, null, null, null, null, null, null, null,
        timestamp);
  }

  private static com.guardpay.behavior.dto.BehavioralAnalysisRequest newRequest(
      UUID userId, BigDecimal amount, OffsetDateTime timestamp) {
    return new com.guardpay.behavior.dto.BehavioralAnalysisRequest(
        userId, amount, "USD", timestamp, null, null, null, null, null, null, null, null, null, null,
        timestamp);
  }

  private static List<Transaction> completedHistory(int count, String amount) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> tx(amount, OffsetDateTime.parse("2026-01-" + String.format("%02d", i + 1) + "T10:00:00Z")))
        .toList();
  }

  private static List<Transaction> completedHistoryAtHour(int count, int hour) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> tx("1000", OffsetDateTime.parse("2026-01-" + String.format("%02d", i + 1) + "T" + String.format("%02d", hour) + ":00:00Z")))
        .toList();
  }

  private static Transaction tx(String amount, OffsetDateTime timestamp) {
    Transaction t = new Transaction();
    t.setAmount(new BigDecimal(amount));
    t.setCurrency("USD");
    t.setStatus(TransactionStatus.COMPLETED);
    t.setTransactionTimestamp(timestamp);
    t.setTransactionType(TransactionType.PAYMENT);
    return t;
  }

  private void mockHistory(List<Transaction> history) {
    org.mockito.Mockito.doReturn(new PageImpl<>(history))
        .when(transactions)
        .findByUserId(org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.any(Pageable.class));
  }

  private void mockEmptyHistory() {
    mockHistory(List.of());
  }

  @Test
  void currencyMatchingDetectsAmountDeviation() {
    // 25 USD transactions, then a large USD payment
    List<Transaction> history = new ArrayList<>(completedHistory(25, "1000"));
    history.add(tx("50000", OffsetDateTime.parse("2026-01-26T10:00:00Z"))); // current txn, excluded from baseline
    mockHistory(history.subList(0, 25)); // only the 25 historical ones

    var request = new com.guardpay.behavior.dto.BehavioralAnalysisRequest(
        userId, new BigDecimal("50000"), "USD",
        OffsetDateTime.parse("2026-01-26T10:00:00Z"),
        null, null, null, null, null, null, null, null, null, null,
        OffsetDateTime.parse("2026-01-26T10:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
    boolean hasAmountFinding =
        response.findings().stream().anyMatch(f -> "AMOUNT_DEVIATION".equals(f.code()));
    assertTrue(hasAmountFinding, "Expected AMOUNT_DEVIATION for 50000 USD vs 1000 USD median");
  }

  @Test
  void currencyMismatchDoesNotTriggerAmountDeviation() {
    // 25 USD transactions, then a EUR payment - should not trigger amount deviation
    // because there's no EUR baseline
    List<Transaction> history = completedHistory(25, "1000");
    mockHistory(history);

    var request = new com.guardpay.behavior.dto.BehavioralAnalysisRequest(
        userId, new BigDecimal("50000"), "EUR",
        OffsetDateTime.parse("2026-01-26T10:00:00Z"),
        null, null, null, null, null, null, null, null, null, null,
        OffsetDateTime.parse("2026-01-26T10:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
    boolean hasAmountFinding =
        response.findings().stream().anyMatch(f -> "AMOUNT_DEVIATION".equals(f.code()));
    assertFalse(hasAmountFinding, "Should NOT trigger AMOUNT_DEVIATION for EUR with USD-only baseline");
  }

  @Test
  void inrCurrencyBaseline() {
    // 25 INR transactions, then a large INR payment
    List<Transaction> inrHistory = java.util.stream.IntStream.range(0, 25)
        .mapToObj(i -> tx("50000", OffsetDateTime.parse("2026-01-" + String.format("%02d", i + 1) + "T10:00:00Z")))
        .peek(t -> t.setCurrency("INR"))
        .toList();
    mockHistory(inrHistory);

    var request = new com.guardpay.behavior.dto.BehavioralAnalysisRequest(
        userId, new BigDecimal("500000"), "INR",
        OffsetDateTime.parse("2026-01-26T10:00:00Z"),
        null, null, null, null, null, null, null, null, null, null,
        OffsetDateTime.parse("2026-01-26T10:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
    boolean hasAmountFinding =
        response.findings().stream().anyMatch(f -> "AMOUNT_DEVIATION".equals(f.code()));
    assertTrue(hasAmountFinding, "Expected AMOUNT_DEVIATION for 500000 INR vs 50000 INR median");
  }

  @Test
  void timezoneAwareHourAnalysis() {
    // 25 transactions all at 10:00 UTC (business hours in UTC)
    List<Transaction> history = completedHistoryAtHour(25, 10);
    mockHistory(history);

    // Payment at 3 AM UTC = 10 PM previous day in US/Eastern (still business hours-ish)
    // But 3 AM UTC is night in most timezones
    var request = new com.guardpay.behavior.dto.BehavioralAnalysisRequest(
        userId, new BigDecimal("1000"), "USD",
        OffsetDateTime.parse("2026-01-26T03:00:00Z"),
        null, null, null, null, null, null, null, null, null, null,
        OffsetDateTime.parse("2026-01-26T03:00:00Z"));

    // With UTC timezone (default), 3 AM is night → should trigger UNUSUAL_PAYMENT_HOUR
    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
    boolean hasTimeFinding =
        response.findings().stream().anyMatch(f -> "UNUSUAL_PAYMENT_HOUR".equals(f.code()));
    assertTrue(hasTimeFinding, "Expected UNUSUAL_PAYMENT_HOUR for 3 AM UTC with UTC timezone");
  }

  @Test
  void invalidTimezoneFallsBackToUtc() {
    // 25 transactions all at 10:00 UTC
    List<Transaction> history = completedHistoryAtHour(25, 10);
    mockHistory(history);

    var request = new com.guardpay.behavior.dto.BehavioralAnalysisRequest(
        userId, new BigDecimal("1000"), "USD",
        OffsetDateTime.parse("2026-01-26T03:00:00Z"),
        null, null, null, null, null, null, null, null, null, null,
        OffsetDateTime.parse("2026-01-26T03:00:00Z"));

    var response = service.analyze(request, null);

    assertEquals("ESTABLISHED", response.profileStatus());
    // Invalid/missing timezone defaults to UTC, so 3 AM triggers night detection
    boolean hasTimeFinding =
        response.findings().stream().anyMatch(f -> "UNUSUAL_PAYMENT_HOUR".equals(f.code()));
    assertTrue(hasTimeFinding, "Expected UNUSUAL_PAYMENT_HOUR with default UTC timezone");
  }
}
