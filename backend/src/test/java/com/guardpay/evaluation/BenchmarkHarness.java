package com.guardpay.evaluation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.ai.AiUnavailableException;
import com.guardpay.ai.dto.AiAnalysisResponse;
import com.guardpay.ai.dto.AiFindingDto;
import com.guardpay.behavior.AdaptiveBehavioralAnalyzer;
import com.guardpay.behavior.BehavioralOutcomeEventRepository;
import com.guardpay.behavior.BehavioralProfileService;
import com.guardpay.behavior.UserBehaviorProfileRepository;
import com.guardpay.behavior.UserRecipientBehaviorRepository;
import com.guardpay.behavior.UserHourlyBehaviorRepository;
import com.guardpay.behavior.UserGeoBehaviorRepository;
import com.guardpay.behavior.UserDeviceBehaviorRepository;
import com.guardpay.device.DeviceCallState;
import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientHistoryService;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.recipient.RecipientRiskContext;
import com.guardpay.recipient.RecipientRiskEvaluator;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.correlation.RiskCorrelationEngine;
import com.guardpay.risk.signal.AutomaticRiskEvaluationService;
import com.guardpay.risk.signal.DeterministicContentAnalyzer;
import com.guardpay.risk.signal.PaymentSignalCollectorService;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Runs one scenario through the real automatic evaluation pipeline with fresh
 * mocks. No Spring context, database, PayPal, device, or network is involved.
 */
final class BenchmarkHarness {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID RECIPIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

  Result evaluate(BenchmarkScenario scenario, BenchmarkMode mode) {
    return evaluate(scenario, mode, true, true);
  }

  Result evaluate(
      BenchmarkScenario scenario, BenchmarkMode mode, boolean recipientIntelligence) {
    return evaluate(scenario, mode, recipientIntelligence, true);
  }

  Result evaluate(
      BenchmarkScenario scenario,
      BenchmarkMode mode,
      boolean recipientIntelligence,
      boolean behavioralAnalysis) {
    return evaluate(scenario, mode, recipientIntelligence, behavioralAnalysis, Set.of());
  }

  /**
   * Runs one scenario. {@code excludedHistoryIds} plays the role of the
   * behavioral outcome ledger: those completed payments were refunded,
   * reversed, or disputed and must not contribute to the learned baseline.
   */
  Result evaluate(
      BenchmarkScenario scenario,
      BenchmarkMode mode,
      boolean recipientIntelligence,
      boolean behavioralAnalysis,
      Set<UUID> excludedHistoryIds) {
    return evaluate(
        scenario, mode, recipientIntelligence, behavioralAnalysis, excludedHistoryIds, false);
  }

  Result evaluate(
      BenchmarkScenario scenario,
      BenchmarkMode mode,
      boolean recipientIntelligence,
      boolean behavioralAnalysis,
      Set<UUID> excludedHistoryIds,
      boolean correlation) {
    BenchmarkScenario.Input input = scenario.input();

    UserRepository users = mock(UserRepository.class);
    RecipientRepository recipients = mock(RecipientRepository.class);
    MerchantRepository merchants = mock(MerchantRepository.class);
    TransactionRepository transactions = mock(TransactionRepository.class);
    DeviceCallStateRepository deviceStates = mock(DeviceCallStateRepository.class);
    DemoUserService demoUser = mock(DemoUserService.class);
    AiAnalysisClient aiClient = mock(AiAnalysisClient.class);
    RecipientHistoryService recipientHistory =
        recipientIntelligence
            ? new RecipientHistoryService(transactions)
            : mock(RecipientHistoryService.class);

    User user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    when(demoUser.currentUser()).thenReturn(user);
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    List<Transaction> history = history(input);
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED)).thenReturn(history);
    when(transactions.findByUserAndTransactionTimestampAfter(eq(user), any()))
        .thenReturn(recentAttempts(input));
    when(transactions.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(history));

    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(input.amount());
    dto.setCurrency(input.currency());
    dto.setContentText(input.contentText());
    dto.setContentUrl(input.contentUrl());

    if (input.recipient() != CounterpartyState.NONE) {
      Recipient recipient = new Recipient();
      ReflectionTestUtils.setField(recipient, "id", RECIPIENT_ID);
      recipient.setUser(user);
      recipient.setName("Benchmark recipient");
      when(recipients.findByIdAndUserId(RECIPIENT_ID, USER_ID)).thenReturn(Optional.of(recipient));

      if (recipientIntelligence) {
        when(transactions.findRecipientCompletedHistory(
                eq(USER_ID),
                eq(RECIPIENT_ID),
                eq(input.currency()),
                any(),
                any(),
                any(Pageable.class)))
            .thenReturn(recipientHistory(input));
        when(transactions.countRecipientAttemptsInWindow(
                eq(USER_ID), eq(RECIPIENT_ID), any(), any(), any(), any()))
            .thenReturn((long) input.recentAttempts());
        when(transactions.countRecipientUnsuccessfulAttempts(
                eq(USER_ID), eq(RECIPIENT_ID), any(), any(), any()))
            .thenReturn(0L);
      } else {
        when(recipientHistory.load(any(), any(), any(), any(), any()))
            .thenReturn(baselineContext(input));
      }
      dto.setRecipientId(RECIPIENT_ID);
    }

    if (input.merchant() != CounterpartyState.NONE) {
      Merchant merchant = new Merchant();
      ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
      merchant.setName("Benchmark merchant");
      when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
      when(transactions.findByUserAndMerchantAndStatus(
              user, merchant, TransactionStatus.COMPLETED))
          .thenReturn(input.merchant() == CounterpartyState.KNOWN ? history : List.of());
      dto.setMerchantId(MERCHANT_ID);
    }

    if (input.device() != DeviceState.NONE) {
      DeviceCallState state = new DeviceCallState();
      state.setUser(user);
      state.setDeviceId(DEVICE_ID);
      state.setActiveCall(input.device() == DeviceState.ACTIVE);
      state.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC));
      when(deviceStates.findByUserIdAndDeviceId(USER_ID, DEVICE_ID))
          .thenReturn(Optional.of(state));
      dto.setDeviceId(DEVICE_ID);
    }

    if (mode == BenchmarkMode.DETERMINISTIC_ONLY) {
      when(aiClient.analyze(any()))
          .thenThrow(new AiUnavailableException("AI disabled for deterministic benchmark mode"));
    } else {
      when(aiClient.analyze(any()))
          .thenReturn(
              new AiAnalysisResponse(
                  UUID.randomUUID(),
                  aiFindings(input.aiFindings()),
                  "Synthetic benchmark AI evidence"));
    }

    UserBehaviorProfileRepository profileRepo = mock(UserBehaviorProfileRepository.class);
    UserRecipientBehaviorRepository recipientBehaviorRepo =
        mock(UserRecipientBehaviorRepository.class);
    UserHourlyBehaviorRepository hourlyBehaviorRepo =
        mock(UserHourlyBehaviorRepository.class);
    UserGeoBehaviorRepository geoBehaviorRepo = mock(UserGeoBehaviorRepository.class);
    UserDeviceBehaviorRepository deviceBehaviorRepo =
        mock(UserDeviceBehaviorRepository.class);
    EntityManager entityManager = mock(EntityManager.class);
    BehavioralOutcomeEventRepository outcomeEvents =
        mock(BehavioralOutcomeEventRepository.class);
    when(outcomeEvents.findNonLearnableTransactionIds(USER_ID)).thenReturn(excludedHistoryIds);
    when(outcomeEvents.findTransactionIdsByOutcomeIn(eq(USER_ID), any())).thenReturn(Set.of());

    BehavioralProfileService profileService =
        new BehavioralProfileService(
            transactions,
            profileRepo,
            recipientBehaviorRepo,
            hourlyBehaviorRepo,
            geoBehaviorRepo,
            deviceBehaviorRepo,
            entityManager,
            outcomeEvents,
            null);
    AdaptiveBehavioralAnalyzer behavioralAnalyzer =
        behavioralAnalysis ? new AdaptiveBehavioralAnalyzer(profileService) : null;

    PaymentSignalCollectorService collector =
        new PaymentSignalCollectorService(
            users,
            recipients,
            merchants,
            transactions,
            new DeterministicContentAnalyzer(),
            recipientHistory,
            new RecipientRiskEvaluator());
    AutomaticRiskEvaluationService service =
        correlation
            ? new AutomaticRiskEvaluationService(
                collector,
                new DeterministicRiskEngine(),
                demoUser,
                deviceStates,
                recipients,
                merchants,
                aiClient,
                null,
                behavioralAnalyzer,
                new RiskCorrelationEngine())
            : new AutomaticRiskEvaluationService(
                collector,
                new DeterministicRiskEngine(),
                demoUser,
                deviceStates,
                recipients,
                merchants,
                aiClient,
                null,
                behavioralAnalyzer);

    long startedAt = System.nanoTime();
    AutomaticEvaluationResponseDto response = service.evaluate(dto);
    return new Result(response, System.nanoTime() - startedAt);
  }

  private static List<Transaction> history(BenchmarkScenario.Input input) {
    List<Transaction> history = new ArrayList<>();
    int index = 0;
    for (BigDecimal amount : input.historyAmounts()) {
      Transaction transaction = transaction(input.currency(), amount);
      ReflectionTestUtils.setField(transaction, "id", historyId(index++));
      history.add(transaction);
    }
    return List.copyOf(history);
  }

  /** Ids of the completed history payments, matching {@link #history}. */
  static Set<UUID> historyIds(BenchmarkScenario.Input input) {
    Set<UUID> ids = new HashSet<>();
    for (int index = 0; index < input.historyAmounts().size(); index++) {
      ids.add(historyId(index));
    }
    return ids;
  }

  private static UUID historyId(int index) {
    return UUID.nameUUIDFromBytes(
        ("guardpay-benchmark-history:" + index).getBytes(StandardCharsets.UTF_8));
  }

  private static List<Transaction> recipientHistory(BenchmarkScenario.Input input) {
    if (input.recipient() != CounterpartyState.KNOWN) {
      return List.of();
    }
    List<BigDecimal> amounts =
        input.recipientHistoryAmounts() == null
            ? input.historyAmounts()
            : input.recipientHistoryAmounts();
    List<Transaction> history = new ArrayList<>();
    for (BigDecimal amount : amounts) {
      history.add(transaction(input.currency(), amount));
    }
    return List.copyOf(history);
  }

  private static List<Transaction> recentAttempts(BenchmarkScenario.Input input) {
    List<Transaction> attempts = new ArrayList<>();
    for (int i = 0; i < input.recentAttempts(); i++) {
      attempts.add(transaction(input.currency(), new BigDecimal("1.00")));
    }
    return List.copyOf(attempts);
  }

  private static RecipientRiskContext baselineContext(BenchmarkScenario.Input input) {
    boolean hasHistory = !recipientHistory(input).isEmpty();
    return new RecipientRiskContext(
        true,
        null,
        hasHistory ? 1 : 0,
        false,
        0,
        null,
        null,
        false,
        0);
  }

  private static Transaction transaction(String currency, BigDecimal amount) {
    Transaction transaction = new Transaction();
    transaction.setAmount(amount);
    transaction.setCurrency(currency);
    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setTransactionTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
    return transaction;
  }

  private static List<AiFindingDto> aiFindings(List<String> codes) {
    return codes.stream()
        .map(
            code ->
                new AiFindingDto(
                    code,
                    "AI " + code,
                    "Synthetic benchmark finding for " + code,
                    "HIGH",
                    0.9,
                    "Synthetic benchmark evidence for " + code))
        .toList();
  }

  record Result(AutomaticEvaluationResponseDto response, long latencyNanos) {}
}

