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
 * <em>simulated</em> channel. The code is delivered out of band and is never
 * returned by the public API or written to browser state. It is not real
 * identity verification and must be replaced before production.
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
  private final VerificationChannel channel;

  @Value("${guardpay.verification.ttl-minutes:10}")
  private long ttlMinutes;

  @Value("${guardpay.verification.max-attempts:3}")
  private int maxAttempts;

  public PaymentVerificationService(
      PaymentVerificationRepository verifications,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PayPalOrderService orderService,
      VerificationChannel channel) {
    this.verifications = verifications;
    this.transactions = transactions;
    this.assessments = assessments;
    this.orderService = orderService;
    this.channel = channel;
  }

  /**
   * Creates (or returns) the challenge for a payment attempt. Called only from
   * the completion transaction when the engine returned VERIFY.
   */
  GuardedVerificationDto startChallenge(Transaction transaction) {
    PaymentVerification existing = verifications.findByTransaction(transaction).orElse(null);
    if (existing != null) {
      if (existing.getStatus() == VerificationStatus.PENDING
          && !channel.has(transaction.getId())) {
        // The in-memory channel can lose its copy across a restart. Rotate the
        // hash and deliver again rather than stranding a pending challenge.
        issueCode(existing, transaction);
      }
      return toDto(existing);
    }
    PaymentVerification verification = new PaymentVerification();
    verification.setTransaction(transaction);
    verification.setStatus(VerificationStatus.PENDING);
    verification.setAttempts(0);
    verification.setMaxAttempts(maxAttempts);
    verification.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(ttlMinutes));
    issueCode(verification, transaction);
    return toDto(verification);
  }

  private void issueCode(PaymentVerification verification, Transaction transaction) {
    String code = randomCode();
    verification.setChallengeCodeHash(hash(code, transaction.getId()));
    verifications.save(verification);
    try {
      channel.deliver(transaction.getId(), code);
    } catch (IllegalStateException unavailable) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Verification delivery is not configured; no challenge was exposed");
    }
    log.info("Controlled verification challenge issued (simulated out-of-band channel)");
  }

  @Transactional
  public VerificationConfirmResponseDto confirm(UUID paymentId, String code) {
    Transaction transaction =
        transactions
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown payment"));
    PaymentVerification verification =
        verifications
            .findByTransactionIdForUpdate(transaction.getId())
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
      channel.clear(transaction.getId());
      return failure(transaction, verification, "This verification has expired. Start a new payment attempt.");
    }

    if (!codeMatches(verification, transaction, code)) {
      verification.setAttempts(verification.getAttempts() + 1);
      if (verification.getAttempts() >= verification.getMaxAttempts()) {
        verification.setStatus(VerificationStatus.FAILED);
        channel.clear(transaction.getId());
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
    // Clear only after the order exists: if PayPal creation fails, the
    // transaction and verification roll back and the user may retry the code.
    channel.clear(transaction.getId());
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
      String approvalUrl = transaction.getPaypalApprovalUrl();
      if (approvalUrl == null) {
        // Rows created before the approval URL was persisted can still recover
        // the same PayPal order's approve link instead of returning null or
        // creating a second order.
        approvalUrl = orderService.fetchApprovalUrl(transaction.getPaypalOrderId());
        transaction.setPaypalApprovalUrl(approvalUrl);
        transactions.save(transaction);
      }
      return new GuardedPaypalOrderDto(transaction.getPaypalOrderId(), approvalUrl);
    }
    if (transaction.getRecipient() == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This payment has no verified PayPal destination");
    }
    CreatedPaypalOrder created =
        orderService.createPaypalOrder(
            transaction.getAmount(),
            transaction.getDescription(),
            transaction.getRecipient().getEmail(),
            transaction.getRecipient().getPaypalAccountId());
    transaction.setPaypalOrderId(created.orderId());
    transaction.setPaypalApprovalUrl(created.approvalUrl());
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

  private GuardedVerificationDto toDto(PaymentVerification verification) {
    boolean simulated = true;
    return new GuardedVerificationDto(
        verification.getId(),
        verification.getStatus(),
        verification.getExpiresAt(),
        Math.max(0, verification.getMaxAttempts() - verification.getAttempts()),
        simulated,
        "Ask the developer to read the code from the server-side dev outbox; "
            + "it is never returned to the browser.");
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
