package com.guardpay.paypal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.payment.PaymentContextRepository;
import com.guardpay.paypal.client.PayPalClient;
import com.guardpay.paypal.dto.CreateOrderRequestDto;
import com.guardpay.paypal.dto.CreateOrderResponseDto;
import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.dto.PayPalAmount;
import com.guardpay.paypal.dto.PayPalCaptureResponse;
import com.guardpay.paypal.dto.PayPalCreateOrderRequest;
import com.guardpay.paypal.dto.PayPalCreateOrderResponse;
import com.guardpay.paypal.dto.PayPalLink;
import com.guardpay.paypal.exception.CaptureBlockedException;
import com.guardpay.paypal.exception.PayPalException;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RiskLevel;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Order flow with mocked PayPal HTTP and mocked repositories — no network, no
 * database. Proves the server-side risk gate, never the frontend's word.
 * Stubs are declared per test (Mockito strict stubs).
 */
@ExtendWith(MockitoExtension.class)
class PayPalOrderServiceTest {

  @Mock private PayPalClient payPalClient;
  @Mock private TransactionRepository transactions;
  @Mock private PaymentContextRepository contexts;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private UserRepository users;

  private PayPalOrderService service;

  @BeforeEach
  void setUp() {
    service =
        new PayPalOrderService(
            payPalClient, new DeterministicRiskEngine(), transactions, contexts, assessments, users);
  }

  /** Repositories echo saved entities (no database). Call only where saves happen. */
  private void stubEchoSaves() {
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(contexts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(assessments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(users.findByExternalUserId(anyString())).thenReturn(Optional.empty());
  }

  private static CreateOrderRequestDto request(String amount, String average, boolean flags) {
    CreateOrderRequestDto dto = new CreateOrderRequestDto();
    dto.setAmount(new BigDecimal(amount));
    dto.setCurrency("USD");
    dto.setDescription("GuardPay protected payment");
    dto.setHistoricalAverage(average == null ? null : new BigDecimal(average));
    dto.setActiveCall(flags);
    dto.setNewRecipient(flags);
    dto.setUnknownMerchant(flags);
    dto.setUnexpectedInvoice(flags);
    dto.setUrgentLanguage(flags);
    dto.setSuspiciousUrl(flags);
    dto.setSocialEngineeringDetected(flags);
    return dto;
  }

  private static PayPalCreateOrderResponse paypalOrder(String id) {
    return new PayPalCreateOrderResponse(
        id,
        "CREATED",
        List.of(
            new PayPalLink("https://www.sandbox.paypal.com/checkoutnow?token=" + id, "self", "GET"),
            new PayPalLink(
                "https://www.sandbox.paypal.com/checkoutnow?token=" + id, "approve", "GET")));
  }

  @Test
  void allowCreatesPayPalOrderAndStoresDecision() {
    stubEchoSaves();
    when(payPalClient.post(
            eq("/v2/checkout/orders"),
            any(PayPalCreateOrderRequest.class),
            eq(PayPalCreateOrderResponse.class)))
        .thenReturn(paypalOrder("ORDER-1"));

    CreateOrderResponseDto response = service.createOrder(request("45.00", "87", false));

    assertTrue(response.allowed());
    assertEquals("ORDER-1", response.orderId());
    assertEquals("CREATED", response.status());
    assertTrue(response.approvalUrl().contains("sandbox.paypal.com"));
    assertEquals(RiskLevel.LOW, response.riskLevel());
    assertEquals(RecommendedAction.ALLOW, response.recommendedAction());

    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions, times(2)).save(saved.capture());
    Transaction stored = saved.getAllValues().get(1);
    assertEquals("ORDER-1", stored.getPaypalOrderId());
    assertEquals(TransactionStatus.PENDING, stored.getStatus());
    assertEquals(0, new BigDecimal("45.00").compareTo(stored.getAmount()));
  }

  @Test
  void stopCreatesNoPayPalOrder() {
    stubEchoSaves();

    CreateOrderResponseDto response = service.createOrder(request("2499.00", "87", true));

    assertFalse(response.allowed());
    assertNull(response.orderId());
    assertNull(response.approvalUrl());
    assertEquals(RiskLevel.CRITICAL, response.riskLevel());
    assertEquals(RecommendedAction.STOP, response.recommendedAction());

    verify(payPalClient, never()).post(anyString(), any(), any(Class.class));

    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions).save(saved.capture());
    assertEquals(TransactionStatus.CANCELLED, saved.getValue().getStatus());
  }

  @Test
  void verifyCreatesNoPayPalOrder() {
    stubEchoSaves();
    CreateOrderRequestDto dto = request("45.00", "87", false);
    dto.setActiveCall(true);
    dto.setNewRecipient(true);
    dto.setUnexpectedInvoice(true);
    dto.setUrgentLanguage(true); // 15 + 20 + 15 + 10 = 60 -> MEDIUM/VERIFY

    CreateOrderResponseDto response = service.createOrder(dto);

    assertFalse(response.allowed());
    assertNull(response.orderId());
    assertEquals(RiskLevel.MEDIUM, response.riskLevel());
    assertEquals(RecommendedAction.VERIFY, response.recommendedAction());
    verify(payPalClient, never()).post(anyString(), any(), any(Class.class));
  }

  @Test
  void missingApprovalUrlIsControlledError() {
    stubEchoSaves();
    when(payPalClient.post(
            eq("/v2/checkout/orders"),
            any(PayPalCreateOrderRequest.class),
            eq(PayPalCreateOrderResponse.class)))
        .thenReturn(new PayPalCreateOrderResponse("ORDER-9", "CREATED", List.of()));

    PayPalException failure =
        assertThrows(PayPalException.class, () -> service.createOrder(request("45.00", "87", false)));

    assertTrue(failure.getMessage().contains("approval URL"));
  }

  private Transaction storedTransaction(String orderId) {
    Transaction transaction = new Transaction();
    transaction.setPaypalOrderId(orderId);
    transaction.setAmount(new BigDecimal("45.00"));
    transaction.setCurrency("USD");
    return transaction;
  }

  private RiskAssessment assessment(RecommendedAction action, RiskLevel level, int score) {
    RiskAssessment assessment = new RiskAssessment();
    assessment.setRecommendedAction(action);
    assessment.setRiskLevel(level);
    assessment.setScore(score);
    return assessment;
  }

  private static PayPalCaptureResponse captureResponse() {
    PayPalCaptureResponse.Capture capture =
        new PayPalCaptureResponse.Capture("CAP-1", "COMPLETED", new PayPalAmount("USD", "45.00"));
    PayPalCaptureResponse.CapturePayments payments =
        new PayPalCaptureResponse.CapturePayments(List.of(capture));
    PayPalCaptureResponse.CaptureUnit unit = new PayPalCaptureResponse.CaptureUnit(payments);
    return new PayPalCaptureResponse("ORDER-1", "COMPLETED", List.of(unit));
  }

  @Test
  void allowCaptureCallsPayPalAndCompletes() {
    Transaction transaction = storedTransaction("ORDER-1");
    when(transactions.findByPaypalOrderIdForUpdate("ORDER-1")).thenReturn(Optional.of(transaction));
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction))
        .thenReturn(List.of(assessment(RecommendedAction.ALLOW, RiskLevel.LOW, 0)));
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(payPalClient.post(
            eq("/v2/checkout/orders/ORDER-1/capture"), any(), eq(PayPalCaptureResponse.class)))
        .thenReturn(captureResponse());

    CaptureOrderResponseDto response = service.captureOrder("ORDER-1");

    assertEquals("ORDER-1", response.orderId());
    assertEquals("COMPLETED", response.status());
    assertEquals("CAP-1", response.captureId());
    assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    assertEquals("CAP-1", transaction.getPaypalCaptureId());
  }

  @Test
  void stopBlocksCaptureWithoutPayPalCall() {
    Transaction transaction = storedTransaction("ORDER-9");
    when(transactions.findByPaypalOrderIdForUpdate("ORDER-9")).thenReturn(Optional.of(transaction));
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction))
        .thenReturn(List.of(assessment(RecommendedAction.STOP, RiskLevel.CRITICAL, 100)));

    CaptureBlockedException blocked =
        assertThrows(CaptureBlockedException.class, () -> service.captureOrder("ORDER-9"));

    assertEquals(RecommendedAction.STOP, blocked.getAction());
    assertEquals("ORDER-9", blocked.getOrderId());
    verify(payPalClient, never()).post(anyString(), any(), any(Class.class));
  }

  @Test
  void verifyBlocksCaptureWithoutPayPalCall() {
    Transaction transaction = storedTransaction("ORDER-8");
    when(transactions.findByPaypalOrderIdForUpdate("ORDER-8")).thenReturn(Optional.of(transaction));
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction))
        .thenReturn(List.of(assessment(RecommendedAction.VERIFY, RiskLevel.MEDIUM, 60)));

    assertThrows(CaptureBlockedException.class, () -> service.captureOrder("ORDER-8"));
    verify(payPalClient, never()).post(anyString(), any(), any(Class.class));
  }

  @Test
  void unknownOrderIsRejected() {
    when(transactions.findByPaypalOrderIdForUpdate("NOPE")).thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.captureOrder("NOPE"));
    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }

  @Test
  void malformedOrderIdIsRejectedWithoutLookup() {
    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.captureOrder("../../etc"));
    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
    verify(transactions, never()).findByPaypalOrderIdForUpdate(anyString());
  }

  @Test
  void existingDemoUserIsReused() {
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(contexts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(assessments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    User existing = new User();
    when(users.findByExternalUserId("sandbox-demo-user")).thenReturn(Optional.of(existing));
    when(payPalClient.post(
            eq("/v2/checkout/orders"),
            any(PayPalCreateOrderRequest.class),
            eq(PayPalCreateOrderResponse.class)))
        .thenReturn(paypalOrder("ORDER-2"));

    service.createOrder(request("5.00", "87", false));

    verify(users, never()).save(any());
    assertEquals("sandbox-demo-user", PayPalOrderService.DEMO_USER_EXTERNAL_ID);
  }
}
