package com.guardpay.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.payment.dto.GuardedVerificationDto;
import com.guardpay.payment.dto.VerificationConfirmResponseDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.recipient.Recipient;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.user.User;
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

  private static final UUID OWNER = UUID.randomUUID();

  @Mock private PaymentVerificationRepository verifications;
  @Mock private TransactionRepository transactions;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private PayPalOrderService orderService;
  @Mock private VerificationChannel channel;

  private PaymentVerificationService service;
  private Transaction transaction;
  private String deliveredCode;

  @BeforeEach
  void setUp() {
    service =
        new PaymentVerificationService(
            verifications, transactions, assessments, orderService, channel);
    ReflectionTestUtils.setField(service, "ttlMinutes", 10L);
    ReflectionTestUtils.setField(service, "maxAttempts", 3);
    transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setAmount(new BigDecimal("45.00"));
    transaction.setDescription("Invoice payment");
    User owner = new User();
    ReflectionTestUtils.setField(owner, "id", OWNER);
    transaction.setUser(owner);
    Recipient recipient = new Recipient();
    recipient.setUser(owner);
    recipient.setEmail("merchant@example.com");
    transaction.setRecipient(recipient);
  }

  private GuardedVerificationDto issueChallenge() {
    when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    doAnswer(
            invocation -> {
              deliveredCode = invocation.getArgument(1);
              return null;
            })
        .when(channel)
        .deliver(any(), any());
    return service.startChallenge(transaction);
  }

  private PaymentVerification capturedVerification() {
    ArgumentCaptor<PaymentVerification> captor = ArgumentCaptor.forClass(PaymentVerification.class);
    verify(verifications).save(captor.capture());
    return captor.getValue();
  }

  private void stubChallenge(PaymentVerification verification) {
    when(transactions.findByIdForUpdate(transaction.getId())).thenReturn(Optional.of(transaction));
    when(verifications.findByTransactionIdForUpdate(transaction.getId()))
        .thenReturn(Optional.of(verification));
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
    assertEquals(3, challenge.attemptsRemaining());
    assertTrue(challenge.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));

    PaymentVerification stored = capturedVerification();
    assertEquals(VerificationStatus.PENDING, stored.getStatus());
    assertNotNull(deliveredCode);
    assertEquals(6, deliveredCode.length());
    assertFalse(stored.getChallengeCodeHash().equals(deliveredCode));
    assertEquals(3, stored.getMaxAttempts());
    verify(channel).deliver(transaction.getId(), deliveredCode);
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
  }

  @Test
  void reissuesCodeWhenPendingChallengeIsMissingFromTheChannel() {
    PaymentVerification existing = new PaymentVerification();
    existing.setTransaction(transaction);
    existing.setStatus(VerificationStatus.PENDING);
    existing.setChallengeCodeHash("old-hash");
    existing.setAttempts(0);
    existing.setMaxAttempts(3);
    existing.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
    when(verifications.findByTransaction(transaction)).thenReturn(Optional.of(existing));
    when(channel.has(transaction.getId())).thenReturn(false);
    when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    doAnswer(
            invocation -> {
              deliveredCode = invocation.getArgument(1);
              return null;
            })
        .when(channel)
        .deliver(any(), any());

    service.startChallenge(transaction);

    assertNotNull(deliveredCode);
    assertFalse("old-hash".equals(existing.getChallengeCodeHash()));
    verify(channel).deliver(transaction.getId(), deliveredCode);
  }

  @Test
  void successfulConfirmationCreatesOrderAndVerifies() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stubChallenge(stored);
    stubVerifyDecision();
    when(orderService.createPaypalOrder(any(), any(), any(), any()))
        .thenReturn(new CreatedPaypalOrder("O-VER", "CREATED", "https://sandbox.paypal.com/approve"));
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), deliveredCode);

    assertEquals(VerificationStatus.VERIFIED, result.verificationStatus());
    assertEquals(GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED, result.paymentStatus());
    assertEquals("O-VER", result.paypalOrder().orderId());
    assertEquals("https://sandbox.paypal.com/approve", result.paypalOrder().approvalUrl());
    assertNotNull(stored.getVerifiedAt());
    assertEquals("O-VER", transaction.getPaypalOrderId());
    assertEquals("https://sandbox.paypal.com/approve", transaction.getPaypalApprovalUrl());
    verify(channel).clear(transaction.getId());
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
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
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
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
  }

  @Test
  void expiredChallengeIsRejected() {
    GuardedVerificationDto challenge = issueChallenge();
    PaymentVerification stored = capturedVerification();
    stored.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    stubChallenge(stored);
    when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), deliveredCode);

    assertEquals(VerificationStatus.EXPIRED, result.verificationStatus());
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
    verify(channel).clear(transaction.getId());
  }

  @Test
  void alreadyVerifiedReplaysExistingOrderWithoutNewOrder() {
    issueChallenge();
    PaymentVerification stored = capturedVerification();
    stored.setStatus(VerificationStatus.VERIFIED);
    transaction.setPaypalOrderId("O-EXIST");
    transaction.setPaypalApprovalUrl("https://sandbox.paypal.com/existing");
    stubChallenge(stored);

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), deliveredCode);

    assertEquals(VerificationStatus.VERIFIED, result.verificationStatus());
    assertEquals("O-EXIST", result.paypalOrder().orderId());
    assertEquals("https://sandbox.paypal.com/existing", result.paypalOrder().approvalUrl());
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
    verify(orderService, never()).fetchApprovalUrl(any());
  }

  @Test
  void replayRecoversApprovalUrlForOrderCreatedBeforePersistence() {
    issueChallenge();
    PaymentVerification stored = capturedVerification();
    stored.setStatus(VerificationStatus.VERIFIED);
    transaction.setPaypalOrderId("O-LEGACY");
    stubChallenge(stored);
    when(orderService.fetchApprovalUrl("O-LEGACY"))
        .thenReturn("https://sandbox.paypal.com/recovered");
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    VerificationConfirmResponseDto result = service.confirm(transaction.getId(), deliveredCode);

    assertEquals("https://sandbox.paypal.com/recovered", result.paypalOrder().approvalUrl());
    assertEquals("https://sandbox.paypal.com/recovered", transaction.getPaypalApprovalUrl());
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
  }

  @Test
  void unavailableDeliveryChannelFailsClosedWithoutExposingCode() {
    when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    doThrow(new IllegalStateException("not configured")).when(channel).deliver(any(), any());

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.startChallenge(transaction));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
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
            () -> service.confirm(transaction.getId(), deliveredCode));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    verify(orderService, never()).createPaypalOrder(any(), any(), any(), any());
  }

  @Test
  void unknownPaymentIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(transactions.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.confirm(unknown, "123456"));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }

  @Test
  void missingChallengeIsNotFound() {
    when(transactions.findByIdForUpdate(transaction.getId())).thenReturn(Optional.of(transaction));
    when(verifications.findByTransactionIdForUpdate(transaction.getId()))
        .thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.confirm(transaction.getId(), "123456"));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }
}
