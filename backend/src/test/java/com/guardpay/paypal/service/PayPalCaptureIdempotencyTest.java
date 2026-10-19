package com.guardpay.paypal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.payment.PaymentContextRepository;
import com.guardpay.paypal.auth.PayPalOAuthService;
import com.guardpay.paypal.client.PayPalClient;
import com.guardpay.paypal.config.PayPalProperties;
import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.dto.PayPalAmount;
import com.guardpay.paypal.dto.PayPalCaptureResponse;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RiskLevel;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Capture replay safety: the second request for an already-captured order
 * returns the stored result and never touches PayPal again.
 */
@ExtendWith(MockitoExtension.class)
class PayPalCaptureIdempotencyTest {

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
            payPalClient,
            new DeterministicRiskEngine(),
            transactions,
            contexts,
            assessments,
            users);
  }

  private static PayPalCaptureResponse captureResponse() {
    PayPalCaptureResponse.Capture capture =
        new PayPalCaptureResponse.Capture("CAP-7", "COMPLETED", new PayPalAmount("USD", "5.00"));
    return new PayPalCaptureResponse(
        "ORDER-7",
        "COMPLETED",
        List.of(
            new PayPalCaptureResponse.CaptureUnit(
                new PayPalCaptureResponse.CapturePayments(List.of(capture)))));
  }

  private static RiskAssessment allowDecision() {
    RiskAssessment assessment = new RiskAssessment();
    assessment.setRecommendedAction(RecommendedAction.ALLOW);
    assessment.setRiskLevel(RiskLevel.LOW);
    assessment.setScore(10);
    return assessment;
  }

  @Test
  void repeatedCaptureUsesStoredResult() {
    Transaction transaction = new Transaction();
    transaction.setPaypalOrderId("ORDER-7");
    transaction.setAmount(new BigDecimal("5.00"));
    transaction.setCurrency("USD");
    transaction.setStatus(TransactionStatus.PENDING);
    when(transactions.findByPaypalOrderIdForUpdate("ORDER-7")).thenReturn(Optional.of(transaction));
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction))
        .thenReturn(List.of(allowDecision()));
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(payPalClient.post(
            eq("/v2/checkout/orders/ORDER-7/capture"), any(), eq(PayPalCaptureResponse.class)))
        .thenReturn(captureResponse());

    CaptureOrderResponseDto first = service.captureOrder("ORDER-7");
    CaptureOrderResponseDto second = service.captureOrder("ORDER-7");

    assertEquals("CAP-7", first.captureId());
    assertEquals("CAP-7", second.captureId());
    assertEquals("COMPLETED", second.status());
    assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    verify(payPalClient, times(1))
        .post(eq("/v2/checkout/orders/ORDER-7/capture"), any(), eq(PayPalCaptureResponse.class));
  }
}
