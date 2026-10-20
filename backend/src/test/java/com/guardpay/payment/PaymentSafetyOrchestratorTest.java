package com.guardpay.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.merchant.MerchantRepository;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.GuardedVerificationDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.signal.AutomaticRiskEvaluationService;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orchestrator branching with mocked pipeline and repositories — no network,
 * no database. The engine decision (canned here, real elsewhere) alone
 * routes to order creation or refusal.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSafetyOrchestratorTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private AutomaticRiskEvaluationService automaticRisk;
  @Mock private PayPalOrderService orderService;
  @Mock private TransactionRepository transactions;
  @Mock private PaymentContextRepository contexts;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private UserRepository users;
  @Mock private RecipientRepository recipients;
  @Mock private MerchantRepository merchants;
  @Mock private GuardedPaymentIdempotencyRepository idempotency;
  @Mock private GuardedPaymentLock lock;
  @Mock private PaymentVerificationService verificationService;

  private PaymentSafetyOrchestrator orchestrator;
  private User user;

  @BeforeEach
  void setUp() {
    GuardedPaymentCompletionService completion =
        new GuardedPaymentCompletionService(
            orderService, transactions, contexts, assessments, idempotency, lock,
            verificationService);
    orchestrator =
        new PaymentSafetyOrchestrator(
            automaticRisk, completion, users, recipients, merchants, idempotency);
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    // Lenient: the structural test below never initiates, behavior tests always do.
    lenient().when(users.findById(USER_ID)).thenReturn(Optional.of(user));
  }

  private GuardedPaymentRequestDto request() {
    GuardedPaymentRequestDto dto = new GuardedPaymentRequestDto();
    dto.setAmount(new BigDecimal("45.00"));
    dto.setCurrency("USD");
    dto.setDescription("GuardPay protected payment");
    dto.setUserId(USER_ID);
    return dto;
  }

  private AutomaticEvaluationResponseDto risk(
      int score, RiskLevel level, RecommendedAction action) {
    return new AutomaticEvaluationResponseDto(
        score, level, action, "Explanation.", List.of(), List.of(), "NOT_REQUESTED");
  }

  private void stubEchoSaves() {
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(contexts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(assessments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void safePaymentCreatesOrder() {
    stubEchoSaves();
    when(automaticRisk.evaluate(any()))
        .thenReturn(risk(10, RiskLevel.LOW, RecommendedAction.ALLOW));
    when(orderService.createPaypalOrder(any(), any()))
        .thenReturn(new CreatedPaypalOrder("O-1", "CREATED", "https://sandbox.paypal.com/approve"));

    GuardedPaymentResponseDto response = orchestrator.initiate(request());

    assertEquals(GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED, response.paymentStatus());
    assertEquals("O-1", response.paypalOrder().orderId());
    assertEquals(10, response.risk().score());
    assertTrue(response.paymentId() == null); // echo save returns the unsaved instance

    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions, times(2)).save(saved.capture());
    Transaction stored = saved.getAllValues().get(1);
    assertEquals(TransactionStatus.PENDING, stored.getStatus());
    assertEquals("O-1", stored.getPaypalOrderId());
  }

  @Test
  void stopCreatesNoOrder() {
    stubEchoSaves();
    when(automaticRisk.evaluate(any()))
        .thenReturn(risk(100, RiskLevel.CRITICAL, RecommendedAction.STOP));

    GuardedPaymentResponseDto response = orchestrator.initiate(request());

    assertEquals(GuardedPaymentStatus.BLOCKED, response.paymentStatus());
    assertNull(response.paypalOrder());
    verify(orderService, never()).createPaypalOrder(any(), any());

    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions).save(saved.capture());
    assertEquals(TransactionStatus.CANCELLED, saved.getValue().getStatus());
  }

  @Test
  void verifyCreatesNoOrder() {
    stubEchoSaves();
    when(automaticRisk.evaluate(any()))
        .thenReturn(risk(60, RiskLevel.MEDIUM, RecommendedAction.VERIFY));

    GuardedPaymentResponseDto response = orchestrator.initiate(request());

    assertEquals(GuardedPaymentStatus.VERIFICATION_REQUIRED, response.paymentStatus());
    assertNull(response.paypalOrder());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void verifyIssuesControlledChallengeInsteadOfOrder() {
    stubEchoSaves();
    when(automaticRisk.evaluate(any()))
        .thenReturn(risk(60, RiskLevel.MEDIUM, RecommendedAction.VERIFY));
    GuardedVerificationDto challenge =
        new GuardedVerificationDto(
            UUID.randomUUID(),
            VerificationStatus.PENDING,
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
            3,
            true,
            "simulated",
            "123456");
    when(verificationService.startChallenge(any())).thenReturn(challenge);

    GuardedPaymentResponseDto response = orchestrator.initiate(request());

    assertEquals(GuardedPaymentStatus.VERIFICATION_REQUIRED, response.paymentStatus());
    assertNull(response.paypalOrder());
    assertNotNull(response.verification());
    assertEquals(VerificationStatus.PENDING, response.verification().status());
    verify(verificationService).startChallenge(any());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void unknownRecipientNameIsLearnedAsNew() {
    stubEchoSaves();
    when(automaticRisk.evaluate(any()))
        .thenReturn(risk(20, RiskLevel.LOW, RecommendedAction.ALLOW));
    when(recipients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(orderService.createPaypalOrder(any(), any()))
        .thenReturn(new CreatedPaypalOrder("O-2", "CREATED", "https://sandbox.paypal.com/approve"));

    GuardedPaymentRequestDto dto = request();
    dto.setRecipientName("Brand New Stranger");
    GuardedPaymentResponseDto response = orchestrator.initiate(dto);

    assertEquals(GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED, response.paymentStatus());
    verify(recipients).save(any());
    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions, times(2)).save(saved.capture());
    assertEquals("Brand New Stranger", saved.getAllValues().get(1).getRecipient().getName());
  }

  @Test
  void knownRecipientNameIsReusedWithoutSaving() {
    stubEchoSaves();
    when(automaticRisk.evaluate(any()))
        .thenReturn(risk(20, RiskLevel.LOW, RecommendedAction.ALLOW));
    when(orderService.createPaypalOrder(any(), any()))
        .thenReturn(new CreatedPaypalOrder("O-3", "CREATED", "https://sandbox.paypal.com/approve"));
    Recipient known = new Recipient();
    known.setUser(user);
    known.setName("Known Person");
    when(recipients.findByUserIdAndNameIgnoreCase(USER_ID, "known person"))
        .thenReturn(Optional.of(known));

    GuardedPaymentRequestDto dto = request();
    dto.setRecipientName("known person");
    GuardedPaymentResponseDto response = orchestrator.initiate(dto);

    assertEquals(GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED, response.paymentStatus());
    verify(recipients, never()).save(any());
    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions, times(2)).save(saved.capture());
    assertEquals("Known Person", saved.getAllValues().get(1).getRecipient().getName());
  }

  @Test
  void foreignRecipientIdIsRejectedInsteadOfSilentlyIgnored() {
    User otherOwner = new User();
    ReflectionTestUtils.setField(otherOwner, "id", UUID.randomUUID());
    Recipient foreign = new Recipient();
    foreign.setUser(otherOwner);
    UUID foreignId = UUID.randomUUID();
    when(recipients.findById(foreignId)).thenReturn(Optional.of(foreign));

    GuardedPaymentRequestDto dto = request();
    dto.setRecipientId(foreignId);

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> orchestrator.initiate(dto));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
    verify(automaticRisk, never()).evaluate(any());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void unknownMerchantIdIsRejectedInsteadOfSilentlyIgnored() {
    UUID unknownId = UUID.randomUUID();
    when(merchants.findById(unknownId)).thenReturn(Optional.empty());

    GuardedPaymentRequestDto dto = request();
    dto.setMerchantId(unknownId);

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> orchestrator.initiate(dto));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
    verify(automaticRisk, never()).evaluate(any());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void requestExposesNoRiskControls() {
    // Structural trust boundary: none of these may exist as request fields.
    List<String> forbidden = List.of(
        "riskScore", "riskLevel", "recommendedAction", "activeCall", "newRecipient",
        "unknownMerchant", "socialEngineeringDetected", "suspiciousUrl", "urgentLanguage",
        "aiFinding", "allowed");
    List<String> methods =
        Arrays.stream(GuardedPaymentRequestDto.class.getDeclaredMethods())
            .map(Method::getName)
            .map(String::toLowerCase)
            .toList();
    for (String name : forbidden) {
      assertFalse(
          methods.contains(("get" + name).toLowerCase())
              || methods.contains(("set" + name).toLowerCase())
              || methods.contains(("is" + name).toLowerCase()),
          "Request must not expose " + name);
    }
  }
}
