package com.guardpay.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.payment.dto.GuardedVerificationDto;
import com.guardpay.payment.dto.VerificationConfirmResponseDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * Controlled VERIFY state machine: the backend alone issues the challenge,
 * bounds attempts, enforces expiry, and only creates the PayPal order after a
 * server-verified success.
 */
@ExtendWith(MockitoExtension.class)
class PaymentVerificationServiceTest {

  @Mock private PaymentVerificationRepository verifications;
  @Mock private TransactionRepository transactions;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private PayPalOrderService orderService;

  private PaymentVerificationService service;
  private Transaction transaction;

  @BeforeEach
  void setUp() {
    service = new PaymentVerificationService(verifications, transactions, assessments, orderService);
    ReflectionTestUtils.setField(service, "ttlMinutes", 10L);
    ReflectionTestUtils.setField(service, "maxAttempts", 3);
    transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setAmount(new BigDecimal("45.00"));
    transaction.setDescription("Invoice payment");
  }

  private GuardedVerificationDto issueChallenge() {
    when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    return service.startChallenge(transaction);
  }

  private PaymentVerification capturedVerification() {
    ArgumentCaptor<PaymentVerification> captor = ArgumentCaptor.forClass(PaymentVerification.class);
    verify(verifications).save(captor.capture());
    return captor.getValue();
  }

  private void stubChallenge(PaymentVerification verification) {
    when(transactions.findById(transaction.getId())).thenReturn(Optional.of(transaction));
    when(verifications.findByTransaction(transaction)).thenReturn(Optional.of(verification));
  }

  private void stubVerifyDecision() {
    RiskAssessment assessment = new RiskAssessment();
    assessment.setTransaction(transaction);
    assessment.setRecommendedAction(RecommendedAction.VERIFY);
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction)).thenReturn(List.of(assessment));
  }

  @Test
  void challengeIsIssuedWithoutCreatingPaypalOrder() {
    GuardedVerificationDto challenge = issueChallenge();

    assertEquals(VerificationStatus.PENDING, challenge.status());
    assertTrue(challenge.simulated());
    assertNotNull(challenge.challengeCode());
    assertEquals(6, challenge.challengeCode().length());
    assertEquals(3, challenge.attemptsRemaining());
    assertTrue(challenge.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));

    PaymentVerification stored = capturedVerification();
    assertEquals(VerificationStatus.PENDING, stored.getStatus());
    assertFalse(stored.getChallengeCodeHash().equals(challenge.challengeCode()));
    assertEquals(3, stored.getMaxAttempts());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void successfulConfirmationCreatesOrderAndVerifies() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stubChallenge(stored);
    stubVerifyDecision();
    when(orderService.createPaypalOrder(any(), any()))
        .thenReturn(new CreatedPaypalOrder("O-VER", "CREATED", "https://sandbox.paypal.com/approve"));
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), challenge.challengeCode());

    assertEquals(VerificationStatus.VERIFIED, result.verificationStatus());
    assertEquals(GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED, result.paymentStatus());
    assertEquals("O-VER", result.paypalOrder().orderId());
    assertNotNull(stored.getVerifiedAt());
    assertEquals("O-VER", transaction.getPaypalOrderId());
  }

  @Test
  void wrongCodeIncrementsAttemptsWithoutOrder() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stubChallenge(stored);

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), "000000");

    assertEquals(VerificationStatus.PENDING, result.verificationStatus());
    assertEquals(GuardedPaymentStatus.VERIFICATION_REQUIRED, result.paymentStatus());
    assertEquals(1, stored.getAttempts());
    assertEquals(2, result.attemptsRemaining());
    assertNull(result.paypalOrder());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void exhaustingAttemptsFailsTheChallenge() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stored.setAttempts(stored.getMaxAttempts() - 1);
    stubChallenge(stored);
    when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), "000000");

    assertEquals(VerificationStatus.FAILED, result.verificationStatus());
    assertEquals(0, result.attemptsRemaining());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void expiredChallengeIsRejected() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stored.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    stubChallenge(stored);
    when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), challenge.challengeCode());

    assertEquals(VerificationStatus.EXPIRED, result.verificationStatus());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void alreadyVerifiedReplaysExistingOrderWithoutNewOrder() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stored.setStatus(VerificationStatus.VERIFIED);
    transaction.setPaypalOrderId("O-EXIST");
    stubChallenge(stored);

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), challenge.challengeCode());

    assertEquals(VerificationStatus.VERIFIED, result.verificationStatus());
    assertEquals("O-EXIST", result.paypalOrder().orderId());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void confirmationRequiresAVerifyDecision() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stubChallenge(stored);
    RiskAssessment assessment = new RiskAssessment();
    assessment.setTransaction(transaction);
    assessment.setRecommendedAction(RecommendedAction.ALLOW);
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction)).thenReturn(List.of(assessment));

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class,
            () -> service.confirm(transaction.getId(), challenge.challengeCode()));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    verify(orderService, never()).createPaypalOrder(any(), any());
  }

  @Test
  void unknownPaymentIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(transactions.findById(unknown)).thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.confirm(unknown, "123456"));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }

  @Test
  void missingChallengeIsNotFound() {
    when(transactions.findById(transaction.getId())).thenReturn(Optional.of(transaction));
    when(verifications.findByTransaction(transaction)).thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.confirm(transaction.getId(), "123456"));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }
}
