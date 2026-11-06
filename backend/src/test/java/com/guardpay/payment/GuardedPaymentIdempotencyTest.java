package com.guardpay.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedPaypalOrderDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedRiskDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.signal.AutomaticRiskEvaluationService;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Idempotency-Key behavior with mocked repositories: repeats return the
 * stored result, mismatches are rejected, races resolve to the winner.
 */
@ExtendWith(MockitoExtension.class)
class GuardedPaymentIdempotencyTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private AutomaticRiskEvaluationService automaticRisk;
  @Mock private PayPalOrderService orderService;
  @Mock private TransactionRepository transactions;
  @Mock private PaymentContextRepository contexts;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private DemoUserService demoUser;
  @Mock private RecipientRepository recipients;
  @Mock private MerchantRepository merchants;
  @Mock private GuardedPaymentIdempotencyRepository idempotency;
  @Mock private GuardedPaymentLock lock;
  @Mock private PaymentVerificationService verificationService;

  private PaymentSafetyOrchestrator orchestrator;
  private User user;

  private static final ObjectMapper JSON = new ObjectMapper();

  @BeforeEach
  void setUp() {
    GuardedPaymentCompletionService completion =
        new GuardedPaymentCompletionService(
            orderService, transactions, contexts, assessments, idempotency, lock,
            verificationService);
    orchestrator =
        new PaymentSafetyOrchestrator(
            automaticRisk, completion, demoUser, recipients, merchants, idempotency);
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
  }

  private GuardedPaymentRequestDto request() {
    GuardedPaymentRequestDto dto = new GuardedPaymentRequestDto();
    dto.setAmount(new BigDecimal("45.00"));
    dto.setCurrency("USD");
    dto.setRecipientName("Sam");
    dto.setRecipientEmail("sam@example.com");
    return dto;
  }

  private AutomaticEvaluationResponseDto allowRisk() {
    return new AutomaticEvaluationResponseDto(
        10, RiskLevel.LOW, RecommendedAction.ALLOW, "Ok.", List.of(), List.of(), "NOT_REQUESTED",
        null);
  }

  private void stubHappyAllow() {
    when(demoUser.currentUser()).thenReturn(user);
    when(automaticRisk.evaluate(any())).thenReturn(allowRisk());
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(recipients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(contexts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(assessments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(orderService.createPaypalOrder(any(), any(), any(), any()))
        .thenReturn(new CreatedPaypalOrder("O-1", "CREATED", "https://sandbox.paypal.com/approve"));
  }

  @Test
  void repeatedKeyReturnsStoredResultWithoutReprocessing() throws Exception {
    stubHappyAllow();
    when(idempotency.findByUserIdAndIdempotencyKey(USER_ID, "key-1")).thenReturn(Optional.empty());

    GuardedPaymentResponseDto first = orchestrator.initiate(request(), "key-1");

    ArgumentCaptor<GuardedPaymentIdempotency> rows =
        ArgumentCaptor.forClass(GuardedPaymentIdempotency.class);
    verify(idempotency).save(rows.capture());
    when(idempotency.findByUserIdAndIdempotencyKey(USER_ID, "key-1")).thenReturn(Optional.of(rows.getValue()));

    GuardedPaymentResponseDto second = orchestrator.initiate(request(), "key-1");

    assertEquals(first, second);
    verify(orderService, times(1)).createPaypalOrder(any(), any(), any(), any());
    verify(automaticRisk, times(1)).evaluate(any());
    verify(lock, times(1)).acquire("key-1");
  }

  @Test
  void reusedKeyWithDifferentPaymentIsRejected() {
    when(demoUser.currentUser()).thenReturn(user);
    GuardedPaymentIdempotency row = new GuardedPaymentIdempotency();
    row.setIdempotencyKey("key-1");
    row.setRequestHash("different-hash");
    row.setResponseBody("{}");
    when(idempotency.findByUserIdAndIdempotencyKey(USER_ID, "key-1")).thenReturn(Optional.of(row));

    GuardedPaymentRequestDto other = request();
    other.setAmount(new BigDecimal("99.00"));
    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> orchestrator.initiate(other, "key-1"));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
  }

  @Test
  void winnerCommittedAfterInitialCheckIsReplayedUnderLock() throws Exception {
    when(demoUser.currentUser()).thenReturn(user);
    when(recipients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(automaticRisk.evaluate(any())).thenReturn(allowRisk());
    GuardedPaymentRequestDto dto = request();
    GuardedPaymentResponseDto winnerBody =
        new GuardedPaymentResponseDto(
            UUID.randomUUID(),
            new GuardedRiskDto(10, RiskLevel.LOW, RecommendedAction.ALLOW, "Ok.", List.of(),
                List.of(), "NOT_REQUESTED", null),
            GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED,
            new GuardedPaypalOrderDto("O-WIN", "https://sandbox.paypal.com/approve"),
            null);
    GuardedPaymentIdempotency winner = new GuardedPaymentIdempotency();
    winner.setIdempotencyKey("key-1");
    winner.setRequestHash(GuardedPaymentReplay.requestHash(dto));
    winner.setResponseBody(JSON.writeValueAsString(winnerBody));
    when(idempotency.findByUserIdAndIdempotencyKey(USER_ID, "key-1"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));

    GuardedPaymentResponseDto response = orchestrator.initiate(dto, "key-1");

    assertEquals(winnerBody, response);
    verify(lock).acquire("key-1");
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
    verify(idempotency, never()).save(any());
  }

  @Test
  void blankKeyDisablesDedup() {
    stubHappyAllow();

    // Blank keys behave like absent keys: normal processing, zero idempotency traffic.
    orchestrator.initiate(request(), "   ");

    verify(idempotency, never()).findByUserIdAndIdempotencyKey(eq(USER_ID), any());
    verify(idempotency, never()).save(any());
    verify(lock, never()).acquire(any());
  }
}

