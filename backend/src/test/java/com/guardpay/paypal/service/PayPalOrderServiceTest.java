package com.guardpay.paypal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.payment.GuardedPaymentStatus;
import com.guardpay.payment.PaymentVerification;
import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.payment.VerificationStatus;
import com.guardpay.paypal.client.PayPalClient;
import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.dto.PayPalAmount;
import com.guardpay.paypal.dto.PayPalCaptureResponse;
import com.guardpay.paypal.dto.PayPalCreateOrderRequest;
import com.guardpay.paypal.dto.PayPalCreateOrderResponse;
import com.guardpay.paypal.dto.PayPalLink;
import com.guardpay.paypal.exception.CaptureBlockedException;
import com.guardpay.paypal.exception.PayPalException;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RiskLevel;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
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

/** Order creation and capture with mocked PayPal HTTP and repositories. */
@ExtendWith(MockitoExtension.class)
class PayPalOrderServiceTest {

  private static final UUID OWNER = UUID.randomUUID();

  @Mock private PayPalClient payPalClient;
  @Mock private TransactionRepository transactions;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private PaymentVerificationRepository verifications;

  private PayPalOrderService service;

  @BeforeEach
  void setUp() {
    service = new PayPalOrderService(payPalClient, transactions, assessments, verifications);
  }

  private static PayPalCreateOrderResponse paypalOrder(String id) {
    return new PayPalCreateOrderResponse(
        id,
        "CREATED",
        List.of(
            new PayPalLink("https://www.sandbox.paypal.com/checkoutnow?token=" + id, "self", "GET"),
            new PayPalLink(
                "https://www.sandbox.paypal.com/checkoutnow?token=" + id, "approve", "GET")),
        List.of());
  }

  private static PayPalCaptureResponse captureResponse() {
    PayPalCaptureResponse.Capture capture =
        new PayPalCaptureResponse.Capture("CAP-1", "COMPLETED", new PayPalAmount("USD", "45.00"));
    PayPalCaptureResponse.CapturePayments payments =
        new PayPalCaptureResponse.CapturePayments(List.of(capture));
    PayPalCaptureResponse.CaptureUnit unit = new PayPalCaptureResponse.CaptureUnit(payments);
    return new PayPalCaptureResponse("ORDER-1", "COMPLETED", List.of(unit));
  }

  private Transaction storedTransaction(String orderId) {
    Transaction transaction = new Transaction();
    transaction.setPaypalOrderId(orderId);
    transaction.setAmount(new BigDecimal("45.00"));
    transaction.setCurrency("USD");
    User owner = new User();
    ReflectionTestUtils.setField(owner, "id", OWNER);
    transaction.setUser(owner);
    return transaction;
  }

  private RiskAssessment assessment(RecommendedAction action, RiskLevel level, int score) {
    RiskAssessment assessment = new RiskAssessment();
    assessment.setRecommendedAction(action);
    assessment.setRiskLevel(level);
    assessment.setScore(score);
    return assessment;
  }

  @Test
  void reloadedAmountIsNormalizedToTwoDecimals() {
    ArgumentCaptor<PayPalCreateOrderRequest> sent =
        ArgumentCaptor.forClass(PayPalCreateOrderRequest.class);
    when(payPalClient.post(
            eq("/v2/checkout/orders"), sent.capture(), eq(PayPalCreateOrderResponse.class)))
        .thenReturn(paypalOrder("ORDER-1"));

    // transactions.amount is NUMERIC(19,4); a reloaded value arrives as 45.0000.
    service.createPaypalOrder(new BigDecimal("45.0000"), "GuardPay protected payment", "merchant@example.com", null);

    assertEquals("45.00", sent.getValue().purchaseUnits().get(0).amount().value());
  }

  @Test
  void missingApprovalUrlIsControlledError() {
    when(payPalClient.post(
            eq("/v2/checkout/orders"),
            any(PayPalCreateOrderRequest.class),
            eq(PayPalCreateOrderResponse.class)))
        .thenReturn(new PayPalCreateOrderResponse("ORDER-9", "CREATED", List.of(), List.of()));

    PayPalException failure =
        assertThrows(
            PayPalException.class,
            () -> service.createPaypalOrder(new BigDecimal("45.00"), "GuardPay protected payment", "merchant@example.com", null));

    assertTrue(failure.getMessage().contains("approval URL"));
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
    assertEquals(GuardedPaymentStatus.COMPLETED, response.paymentStatus());
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
  void verifiedVerifyDecisionAllowsCapture() {
    Transaction transaction = storedTransaction("ORDER-7");
    when(transactions.findByPaypalOrderIdForUpdate("ORDER-7")).thenReturn(Optional.of(transaction));
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction))
        .thenReturn(List.of(assessment(RecommendedAction.VERIFY, RiskLevel.MEDIUM, 60)));
    PaymentVerification verification = new PaymentVerification();
    verification.setStatus(VerificationStatus.VERIFIED);
    when(verifications.findByTransaction(transaction)).thenReturn(Optional.of(verification));
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(payPalClient.post(
            eq("/v2/checkout/orders/ORDER-7/capture"), any(), eq(PayPalCaptureResponse.class)))
        .thenReturn(captureResponse());

    CaptureOrderResponseDto response = service.captureOrder("ORDER-7");

    assertEquals(GuardedPaymentStatus.COMPLETED, response.paymentStatus());
    assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
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
}
