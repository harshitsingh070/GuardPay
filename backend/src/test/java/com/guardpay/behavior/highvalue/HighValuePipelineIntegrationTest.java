package com.guardpay.behavior.highvalue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.behavior.AdaptiveBehavioralAnalyzer;
import com.guardpay.behavior.BehavioralProfileService;
import com.guardpay.behavior.UserBehaviorProfileRepository;
import com.guardpay.behavior.UserDeviceBehaviorRepository;
import com.guardpay.behavior.UserGeoBehaviorRepository;
import com.guardpay.behavior.UserHourlyBehaviorRepository;
import com.guardpay.behavior.UserRecipientBehaviorRepository;
import com.guardpay.behavior.dto.BehavioralAnalysisRequest;
import com.guardpay.recipient.Recipient;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RiskEvaluationResult;
import com.guardpay.risk.RiskEvaluationRequest;
import com.guardpay.risk.RiskFinding;
import com.guardpay.risk.RiskSignal;
import com.guardpay.risk.signal.AutomaticSignalAdapter;
import com.guardpay.risk.signal.AutomaticSignalCollectionResult;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.transaction.TransactionType;
import com.guardpay.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * End-to-end evidence pipeline for the first-time high-value engine:
 * BehavioralProfileService -> AdaptiveBehavioralAnalyzer -> AutomaticSignalAdapter
 * -> DeterministicRiskEngine. Verifies the deterministic engine remains the only
 * decision authority while high-value findings are propagated as evidence.
 */
class HighValuePipelineIntegrationTest {

  private final TransactionRepository transactions =
      org.mockito.Mockito.mock(TransactionRepository.class);
  private final UserBehaviorProfileRepository profiles =
      org.mockito.Mockito.mock(UserBehaviorProfileRepository.class);
  private final UserRecipientBehaviorRepository recipientBehaviors =
      org.mockito.Mockito.mock(UserRecipientBehaviorRepository.class);
  private final UserHourlyBehaviorRepository hourlyBehaviors =
      org.mockito.Mockito.mock(UserHourlyBehaviorRepository.class);
  private final UserGeoBehaviorRepository geoBehaviors =
      org.mockito.Mockito.mock(UserGeoBehaviorRepository.class);
  private final UserDeviceBehaviorRepository deviceBehaviors =
      org.mockito.Mockito.mock(UserDeviceBehaviorRepository.class);
  private final EntityManager entityManager = org.mockito.Mockito.mock(EntityManager.class);

  private final DeterministicRiskEngine riskEngine = new DeterministicRiskEngine();

  private User user;
  private Recipient establishedRecipient;
  private Recipient newRecipient;
  private OffsetDateTime now;

  @BeforeEach
  void setUp() {
    now = OffsetDateTime.now(ZoneOffset.UTC);

    user = new User();
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

    establishedRecipient = recipient("Established payee");
    newRecipient = recipient("New payee");

    org.mockito.Mockito.lenient()
        .when(profiles.findByUserId(org.mockito.ArgumentMatchers.any(UUID.class)))
        .thenReturn(java.util.Optional.empty());
  }

  @Test
  void highValueFindingReachesDeterministicEngineAsEvidence() {
    mockHistory(history(25, new BigDecimal("100"), establishedRecipient));
    FirstTimeHighValueConfig config = FirstTimeHighValueConfig.defaults();

    BehavioralProfileService profileService = profileService(config);
    AdaptiveBehavioralAnalyzer analyzer = new AdaptiveBehavioralAnalyzer(profileService);

    List<PaymentSignal> signals = analyzer.analyze(request(new BigDecimal("5000")));

    PaymentSignal highValueSignal =
        signals.stream()
            .filter(s -> s.type() == PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE)
            .findFirst()
            .orElseThrow(() -> new AssertionError("High-value signal must be emitted"));
    assertTrue(highValueSignal.detected());

    RiskEvaluationRequest riskRequest =
        AutomaticSignalAdapter.toRiskEvaluationRequest(
            new BigDecimal("5000"),
            new AutomaticSignalCollectionResult(signals, new BigDecimal("100")));
    assertTrue(riskRequest.firstPaymentToRecipientHighValue());

    RiskEvaluationResult result = riskEngine.evaluate(riskRequest);
    assertTrue(
        result.findings().stream()
            .filter(f -> f.code().equals(RiskSignal.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE.code()))
            .anyMatch(RiskFinding::triggered),
        "Deterministic engine must evaluate the high-value evidence");
  }

  @Test
  void disabledEngineDoesNotEmitHighValueEvidence() {
    mockHistory(history(25, new BigDecimal("100"), establishedRecipient));
    FirstTimeHighValueConfig config = FirstTimeHighValueConfig.defaults();
    config.setEnabled(false);

    BehavioralProfileService profileService = profileService(config);
    AdaptiveBehavioralAnalyzer analyzer = new AdaptiveBehavioralAnalyzer(profileService);

    List<PaymentSignal> signals = analyzer.analyze(request(new BigDecimal("5000")));

    assertFalse(
        signals.stream()
            .anyMatch(s -> s.type() == PaymentSignalType.FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE));
  }

  private BehavioralProfileService profileService(FirstTimeHighValueConfig config) {
    return new BehavioralProfileService(
        transactions,
        profiles,
        recipientBehaviors,
        hourlyBehaviors,
        geoBehaviors,
        deviceBehaviors,
        entityManager,
        null,
        null,
        new FirstTimeHighValueDetectionEngine(config));
  }

  private BehavioralAnalysisRequest request(BigDecimal amount) {
    return new BehavioralAnalysisRequest(
        user.getId(),
        amount,
        "USD",
        now,
        newRecipient.getId(),
        newRecipient.getName(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        now);
  }

  private void mockHistory(List<Transaction> history) {
    org.mockito.Mockito.doReturn(history)
        .when(transactions)
        .findCompletedHistoryBefore(
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.any(Pageable.class));
  }

  private List<Transaction> history(int count, BigDecimal amount, Recipient recipient) {
    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      history.add(tx(amount, recipient, now.minusDays(80L - i * 3L)));
    }
    return history;
  }

  private Transaction tx(BigDecimal amount, Recipient recipient, OffsetDateTime timestamp) {
    Transaction transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setUser(user);
    transaction.setAmount(amount);
    transaction.setCurrency("USD");
    transaction.setRecipient(recipient);
    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setTransactionTimestamp(timestamp);
    transaction.setTransactionType(TransactionType.PAYMENT);
    return transaction;
  }

  private Recipient recipient(String name) {
    Recipient recipient = new Recipient();
    ReflectionTestUtils.setField(recipient, "id", UUID.randomUUID());
    recipient.setUser(user);
    recipient.setName(name);
    return recipient;
  }
}
