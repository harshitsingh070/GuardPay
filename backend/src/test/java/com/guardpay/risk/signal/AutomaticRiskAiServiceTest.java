package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.ai.AiUnavailableException;
import com.guardpay.ai.dto.AiAnalysisRequest;
import com.guardpay.ai.dto.AiAnalysisResponse;
import com.guardpay.ai.dto.AiFindingDto;
import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AI-augmented path with a mocked AI client and mocked repositories:
 * evidence merges, failures degrade, and the engine alone decides.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticRiskAiServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();
  private static final UUID MERCHANT_ID = UUID.randomUUID();

  @Mock private DemoUserService demoUser;
  @Mock private UserRepository users;
  @Mock private RecipientRepository recipients;
  @Mock private MerchantRepository merchants;
  @Mock private TransactionRepository transactions;
  @Mock private DeviceCallStateRepository deviceStates;
  @Mock private AiAnalysisClient aiClient;

  private AutomaticRiskEvaluationService service;
  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    PaymentSignalCollectorService collector =
        new PaymentSignalCollectorService(users, recipients, merchants, transactions);
    service =
        new AutomaticRiskEvaluationService(
            collector, new DeterministicRiskEngine(), demoUser, deviceStates, recipients, merchants,
            aiClient);
    lenient().when(demoUser.currentUser()).thenReturn(user);
    lenient()
        .when(transactions.findByUserAndTransactionTimestampAfter(any(), any()))
        .thenReturn(List.of());
  }

  private static Transaction completed(String amount) {
    Transaction transaction = new Transaction();
    transaction.setAmount(new BigDecimal(amount));
    transaction.setCurrency("USD");
    transaction.setStatus(TransactionStatus.COMPLETED);
    return transaction;
  }

  private AutomaticEvaluationRequestDto request(String amount) {
    AutomaticEvaluationRequestDto dto = requestWithoutContent(amount);
    dto.setContentText("Pay this invoice immediately.");
    return dto;
  }

  private AutomaticEvaluationRequestDto requestWithoutContent(String amount) {
    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(new BigDecimal(amount));
    return dto;
  }

  /** Content is present (so AI runs) but has no local deterministic indicators. */
  private AutomaticEvaluationRequestDto benignRequest(String amount) {
    AutomaticEvaluationRequestDto dto = requestWithoutContent(amount);
    dto.setContentText("Thanks for the update.");
    return dto;
  }

  private static AiFindingDto aiFinding(String code, double confidence) {
    return new AiFindingDto(code, code + " title", code + " description", "HIGH", confidence,
        "evidence for " + code);
  }

  private static AiAnalysisResponse aiResponse(AiFindingDto... findings) {
    return new AiAnalysisResponse(UUID.randomUUID(), List.of(findings), "Summary.");
  }

  @Test
  void aiUnavailableKeepsDeterministicDecision() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(
            List.of(
                completed("50"), completed("87"), completed("120"), completed("80"),
                completed("98")));
    Recipient fresh = new Recipient();
    ReflectionTestUtils.setField(fresh, "id", RECIPIENT_ID);
    fresh.setUser(user);
    when(recipients.findByIdAndUserId(RECIPIENT_ID, USER_ID)).thenReturn(Optional.of(fresh));
    when(aiClient.analyze(any())).thenThrow(new AiUnavailableException("down"));

    AutomaticEvaluationRequestDto dto = request("2499");
    dto.setRecipientId(RECIPIENT_ID);
    AutomaticEvaluationResponseDto response = service.evaluate(dto);

    // Deterministic content analysis alone reaches STOP when AI is down.
    assertEquals(85, response.score());
    assertEquals(RiskLevel.CRITICAL, response.riskLevel());
    assertEquals(RecommendedAction.STOP, response.recommendedAction());
    assertEquals("UNAVAILABLE", response.aiStatus());
  }

  @Test
  void aiFindingsMergeIntoEngineDecision() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());
    when(aiClient.analyze(any()))
        .thenReturn(aiResponse(aiFinding("URGENT_LANGUAGE", 0.9), aiFinding("IMPERSONATION", 0.8)));

    AutomaticEvaluationResponseDto response = service.evaluate(benignRequest("45"));

    // Deterministic baseline is 0; AI adds urgent (10) + social (20).
    assertEquals(30, response.score());
    assertEquals(RiskLevel.LOW, response.riskLevel());
    assertEquals(RecommendedAction.ALLOW, response.recommendedAction());
    assertEquals("AVAILABLE", response.aiStatus());
    assertTrue(
        response.automaticSignals().stream()
            .anyMatch(signal -> signal.type() == PaymentSignalType.IMPERSONATION
                && signal.source() == SignalSource.AI));
  }

  @Test
  void repeatedSocialEvidenceCountsOnce() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());
    when(aiClient.analyze(any()))
        .thenReturn(
            aiResponse(
                aiFinding("IMPERSONATION", 0.9),
                aiFinding("SCAM_PATTERN", 0.9),
                aiFinding("PRESSURE_TO_PAY", 0.9),
                aiFinding("SECRECY_REQUEST", 0.9),
                aiFinding("SUSPICIOUS_PAYMENT_INSTRUCTION", 0.9)));

    AutomaticEvaluationResponseDto response = service.evaluate(benignRequest("45"));

    // Five social-group findings collapse onto one +20 engine signal.
    assertEquals(20, response.score());
    assertEquals(5, response.automaticSignals().stream()
        .filter(signal -> signal.source() == SignalSource.AI).count());
  }

  @Test
  void combinedSignalsStillCapAtOneHundred() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of(completed("100")));
    Recipient fresh = new Recipient();
    ReflectionTestUtils.setField(fresh, "id", RECIPIENT_ID);
    fresh.setUser(user);
    when(recipients.findByIdAndUserId(RECIPIENT_ID, USER_ID)).thenReturn(Optional.of(fresh));
    Merchant unknown = new Merchant();
    ReflectionTestUtils.setField(unknown, "id", MERCHANT_ID);
    when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(unknown));
    when(transactions.findByUserAndMerchantAndStatus(user, unknown, TransactionStatus.COMPLETED))
        .thenReturn(List.of());
    when(aiClient.analyze(any()))
        .thenReturn(
            aiResponse(
                aiFinding("URGENT_LANGUAGE", 0.9),
                aiFinding("IMPERSONATION", 0.9),
                aiFinding("SUSPICIOUS_URL_STRUCTURE", 0.9),
                aiFinding("UNEXPECTED_INVOICE_CONTEXT", 0.9)));

    AutomaticEvaluationRequestDto dto = request("600");
    dto.setRecipientId(RECIPIENT_ID);
    dto.setMerchantId(MERCHANT_ID);
    AutomaticEvaluationResponseDto response = service.evaluate(dto);

    // 20 + 20 + 10 + 15 + 10 + 20 + 10 = 105 -> capped at 100.
    assertEquals(100, response.score());
    assertEquals(RiskLevel.CRITICAL, response.riskLevel());
    assertEquals(RecommendedAction.STOP, response.recommendedAction());
  }

  @Test
  void severityNeverDecidesAlone() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());
    when(aiClient.analyze(any()))
        .thenReturn(aiResponse(aiFinding("URGENT_LANGUAGE", 0.99)));

    AutomaticEvaluationResponseDto response = service.evaluate(benignRequest("45"));

    // One HIGH-severity AI finding is +10 evidence, still LOW/ALLOW by engine math.
    assertEquals(10, response.score());
    assertEquals(RecommendedAction.ALLOW, response.recommendedAction());
  }

  @Test
  void foreignRecipientNameIsNotSentToAiAndIsNotTreatedAsNew() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());
    when(recipients.findByIdAndUserId(RECIPIENT_ID, USER_ID)).thenReturn(Optional.empty());
    when(aiClient.analyze(any())).thenReturn(aiResponse());

    AutomaticEvaluationRequestDto dto = benignRequest("45");
    dto.setRecipientId(RECIPIENT_ID);
    AutomaticEvaluationResponseDto response = service.evaluate(dto);

    ArgumentCaptor<AiAnalysisRequest> aiRequest = ArgumentCaptor.forClass(AiAnalysisRequest.class);
    verify(aiClient).analyze(aiRequest.capture());
    assertNull(aiRequest.getValue().transaction().recipientName());
    assertFalse(aiRequest.getValue().transaction().isNewRecipient());
    assertEquals(0, response.score());
  }

  @Test
  void withoutContentAiIsNeverCalled() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());

    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(new BigDecimal("45"));
    AutomaticEvaluationResponseDto response = service.evaluate(dto);

    assertEquals(0, response.score());
    assertEquals("NOT_REQUESTED", response.aiStatus());
    verifyNoInteractions(aiClient);
  }
}
