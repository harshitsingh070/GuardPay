package com.guardpay.evaluation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.ai.AiUnavailableException;
import com.guardpay.ai.dto.AiAnalysisResponse;
import com.guardpay.ai.dto.AiFindingDto;
import com.guardpay.device.DeviceCallState;
import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.DeterministicRiskEngine;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    BenchmarkScenario.Input input = scenario.input();

    UserRepository users = mock(UserRepository.class);
    RecipientRepository recipients = mock(RecipientRepository.class);
    MerchantRepository merchants = mock(MerchantRepository.class);
    TransactionRepository transactions = mock(TransactionRepository.class);
    DeviceCallStateRepository deviceStates = mock(DeviceCallStateRepository.class);
    DemoUserService demoUser = mock(DemoUserService.class);
    AiAnalysisClient aiClient = mock(AiAnalysisClient.class);

    User user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    when(demoUser.currentUser()).thenReturn(user);
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    List<Transaction> history = history(input);
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED)).thenReturn(history);
    when(transactions.findByUserAndTransactionTimestampAfter(eq(user), any()))
        .thenReturn(recentAttempts(input));

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
      when(recipients.findById(RECIPIENT_ID)).thenReturn(Optional.of(recipient));
      when(transactions.findByUserAndRecipientAndStatus(
              user, recipient, TransactionStatus.COMPLETED))
          .thenReturn(input.recipient() == CounterpartyState.KNOWN ? history : List.of());
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
      state.setDeviceId(DEVICE_ID);
      state.setActiveCall(input.device() == DeviceState.ACTIVE);
      state.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC));
      when(deviceStates.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(state));
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

    PaymentSignalCollectorService collector =
        new PaymentSignalCollectorService(
            users,
            recipients,
            merchants,
            transactions,
            new DeterministicContentAnalyzer());
    AutomaticRiskEvaluationService service =
        new AutomaticRiskEvaluationService(
            collector,
            new DeterministicRiskEngine(),
            demoUser,
            deviceStates,
            recipients,
            merchants,
            aiClient);

    long startedAt = System.nanoTime();
    AutomaticEvaluationResponseDto response = service.evaluate(dto);
    return new Result(response, System.nanoTime() - startedAt);
  }

  private static List<Transaction> history(BenchmarkScenario.Input input) {
    List<Transaction> history = new ArrayList<>();
    for (BigDecimal amount : input.historyAmounts()) {
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

  private static Transaction transaction(String currency, BigDecimal amount) {
    Transaction transaction = new Transaction();
    transaction.setAmount(amount);
    transaction.setCurrency(currency);
    transaction.setStatus(TransactionStatus.COMPLETED);
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
