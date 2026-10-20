package com.guardpay.payment;

import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedPaypalOrderDto;
import com.guardpay.payment.dto.GuardedVerificationDto;
import com.guardpay.payment.dto.VerificationConfirmResponseDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controlled VERIFY state machine.
 *
 * <p>A VERIFY decision never creates a PayPal order. The backend issues a
 * challenge tied to the payment attempt, records its own outcome, and only
 * after a server-verified success does it create the PayPal order. Expiry and
 * a bounded attempt count stop replay/brute-force; the frontend can never set
 * the outcome, only supply an answer.
 *
 * <p>With no identity provider configured this runs a clearly-labelled
 * <em>simulated</em> channel (the code is returned in the API response). It is
 * not real identity verification and must be replaced before production.
 */
@Service
public class PaymentVerificationService {

  private static final Logger log = LoggerFactory.getLogger(PaymentVerificationService.class);

  static final String SIMULATED_CHANNEL = "SIMULATED";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PaymentVerificationRepository verifications;
  private final TransactionRepository transactions;
  private final RiskAssessmentRepository assessments;
  private final PayPalOrderService orderService;

  @Value("${guardpay.verification.ttl-minutes:10}")
  private long ttlMinutes;

  @Value("${guardpay.verification.max-attempts:3}")
  private int maxAttempts;

  public PaymentVerificationService(
      PaymentVerificationRepository verifications,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PayPalOrderService orderService) {
    this.verifications = verifications;
    this.transactions = transactions;
    this.assessments = assessments;
    this.orderService = orderService;
  }

  /**
   * Creates (or returns) the challenge for a payment attempt. Called only from
   * the completion transaction when the engine returned VERIFY.
   */
  GuardedVerificationDto startChallenge(Transaction transaction) {
    PaymentVerification existing = verifications.findByTransaction(transaction).orElse(null);
    if (existing != null) {
      return toDto(existing, null);
    }
    String code = randomCode();
    PaymentVerification verification = new PaymentVerification();
    verification.setTransaction(transaction);
    verification.setStatus(VerificationStatus.PENDING);
    verification.setChallengeCodeHash(hash(code, transaction.getId()));
    verification.setAttempts(0);
    verification.setMaxAttempts(maxAttempts);
    verification.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(ttlMinutes));
    verifications.save(verification);
    log.info("Controlled verification challenge issued (simulated channel)");
    return toDto(verification, code);
  }

  @Transactional
  public VerificationConfirmResponseDto confirm(UUID paymentId, String code) {
    Transaction transaction =
        transactions
            .findById(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown payment"));
    PaymentVerification verification =
        verifications
            .findByTransaction(transaction)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No verification is pending for this payment"));

    return switch (verification.getStatus()) {
      case VERIFIED -> verifiedSuccess(transaction, verification);
      case FAILED -> failure(transaction, verification, "Verification failed. Start a new payment attempt.");
      case EXPIRED -> failure(transaction, verification, "This verification has expired. Start a new payment attempt.");
      case PENDING -> confirmPending(transaction, verification, code);
    };
  }

  private VerificationConfirmResponseDto confirmPending(
      Transaction transaction, PaymentVerification verification, String code) {
    if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(verification.getExpiresAt())) {
      verification.setStatus(VerificationStatus.EXPIRED);
      verifications.save(verification);
      return failure(transaction, verification, "This verification has expired. Start a new payment attempt.");
    }

    if (!codeMatches(verification, transaction, code)) {
      verification.setAttempts(verification.getAttempts() + 1);
      if (verification.getAttempts() >= verification.getMaxAttempts()) {
        verification.setStatus(VerificationStatus.FAILED);
      }
      verifications.save(verification);
      String message =
          verification.getStatus() == VerificationStatus.FAILED
              ? "Verification failed. Start a new payment attempt."
              : "That code did not match. Please try again.";
      return failure(transaction, verification, message);
    }

    RiskAssessment decision =
        assessments.findByTransactionOrderByCreatedAtDesc(transaction).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.CONFLICT, "No risk decision for this payment"));
    if (decision.getRecommendedAction() != RecommendedAction.VERIFY) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This payment does not require verification");
    }

    verification.setStatus(VerificationStatus.VERIFIED);
    verification.setVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
    verifications.save(verification);
    log.info("Controlled verification succeeded; creating PayPal order");
    return verifiedSuccess(transaction, verification);
  }

  private VerificationConfirmResponseDto verifiedSuccess(
      Transaction transaction, PaymentVerification verification) {
    GuardedPaypalOrderDto order = ensurePaypalOrder(transaction);
    return new VerificationConfirmResponseDto(
        transaction.getId(),
        VerificationStatus.VERIFIED,
        GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED,
        order,
        verification.getExpiresAt(),
        Math.max(0, verification.getMaxAttempts() - verification.getAttempts()),
        "Verification complete. Continue to PayPal Sandbox to approve the payment.");
  }

  private GuardedPaypalOrderDto ensurePaypalOrder(Transaction transaction) {
    if (transaction.getPaypalOrderId() != null) {
      return new GuardedPaypalOrderDto(transaction.getPaypalOrderId(), null);
    }
    CreatedPaypalOrder created =
        orderService.createPaypalOrder(transaction.getAmount(), transaction.getDescription());
    transaction.setPaypalOrderId(created.orderId());
    transaction.setStatus(TransactionStatus.PENDING);
    transactions.save(transaction);
    return new GuardedPaypalOrderDto(created.orderId(), created.approvalUrl());
  }

  private VerificationConfirmResponseDto failure(
      Transaction transaction, PaymentVerification verification, String message) {
    return new VerificationConfirmResponseDto(
        transaction.getId(),
        verification.getStatus(),
        GuardedPaymentStatus.VERIFICATION_REQUIRED,
        null,
        verification.getExpiresAt(),
        Math.max(0, verification.getMaxAttempts() - verification.getAttempts()),
        message);
  }

  private GuardedVerificationDto toDto(PaymentVerification verification, String code) {
    boolean simulated = true;
    return new GuardedVerificationDto(
        verification.getId(),
        verification.getStatus(),
        verification.getExpiresAt(),
        Math.max(0, verification.getMaxAttempts() - verification.getAttempts()),
        simulated,
        "Code delivered through the GuardPay simulated challenge channel.",
        code);
  }

  private boolean codeMatches(PaymentVerification verification, Transaction transaction, String code) {
    if (code == null) {
      return false;
    }
    return MessageDigest.isEqual(
        verification.getChallengeCodeHash().getBytes(StandardCharsets.UTF_8),
        hash(code.trim(), transaction.getId()).getBytes(StandardCharsets.UTF_8));
  }

  private static String randomCode() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
  }

  private static String hash(String code, UUID transactionId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest((transactionId + ":" + code).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required", impossible);
    }
  }
}
