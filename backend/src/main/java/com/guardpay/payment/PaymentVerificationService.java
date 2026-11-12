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
import com.guardpay.user.MfaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * returned by the public API or written to browser state. For local development
 * it can also be printed to the backend terminal (disabled with
 * {@code guardpay.verification.log-code=false}). It is not real identity
 * verification and must be replaced before production.
 */
@Service
public class PaymentVerificationService {

  private static final Logger log = LoggerFactory.getLogger(PaymentVerificationService.class);

  static final String SIMULATED_CHANNEL = "SIMULATED";
  static final String EMAIL_CHANNEL = "EMAIL";
  static final String TOTP_CHANNEL = "TOTP";

  /**
   * Demo-only fixed challenge code. Every simulated (and email) VERIFY
   * challenge uses this value so a demo operator can verify any transaction
   * without reading the outbox or the terminal. Replace with a random code
   * before any real deployment.
   */
  private static final String DEMO_VERIFICATION_CODE = "666666";

  private final PaymentVerificationRepository verifications;
  private final TransactionRepository transactions;
  private final RiskAssessmentRepository assessments;
  private final PayPalOrderService orderService;
  private final VerificationChannel channel;
  private final MfaService mfa;
  private final TotpService totp;
  private final EmailVerificationChannel emailChannel;

  @Value("${guardpay.verification.ttl-minutes:10}")
  private long ttlMinutes;

  @Value("${guardpay.verification.max-attempts:3}")
  private int maxAttempts;

  /**
   * Development-only convenience: print the simulated challenge code to the
   * backend's terminal so a demo operator does not need the loopback outbox.
   * Never enable this outside local development.
   */
  @Value("${guardpay.verification.log-code:true}")
  private boolean logCode;

  /** Legacy/test constructor: no TOTP or email channel configured. */
  public PaymentVerificationService(
      PaymentVerificationRepository verifications,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PayPalOrderService orderService,
      VerificationChannel channel) {
    this(verifications, transactions, assessments, orderService, channel, null, null, null);
  }

  @Autowired
  public PaymentVerificationService(
      PaymentVerificationRepository verifications,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PayPalOrderService orderService,
      VerificationChannel channel,
      MfaService mfa,
      TotpService totp,
      EmailVerificationChannel emailChannel) {
    this.verifications = verifications;
    this.transactions = transactions;
    this.assessments = assessments;
    this.orderService = orderService;
    this.channel = channel;
    this.mfa = mfa;
    this.totp = totp;
    this.emailChannel = emailChannel;
  }

  /**
   * Creates (or returns) the challenge for a payment attempt. Called only from
   * the completion transaction when the engine returned VERIFY.
   */
  GuardedVerificationDto startChallenge(Transaction transaction) {
    String desiredChannel = resolveChannel();
    PaymentVerification existing = verifications.findByTransaction(transaction).orElse(null);
    if (existing != null) {
      existing.setChannel(desiredChannel);
      if (existing.getStatus() == VerificationStatus.PENDING
          && SIMULATED_CHANNEL.equals(desiredChannel)
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
    verification.setChannel(desiredChannel);
    verification.setAttempts(0);
    verification.setMaxAttempts(maxAttempts);
    verification.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(ttlMinutes));
    issueCode(verification, transaction);
    return toDto(verification);
  }

  private String resolveChannel() {
    if (mfa != null && mfa.status().enrolled()) {
      return TOTP_CHANNEL;
    }
    if (emailChannel != null && emailChannel.isConfigured()) {
      return EMAIL_CHANNEL;
    }
    return SIMULATED_CHANNEL;
  }

  private void issueCode(PaymentVerification verification, Transaction transaction) {
    String code = challengeCode();
    verification.setChallengeCodeHash(hash(code, transaction.getId()));
    verifications.save(verification);
    if (TOTP_CHANNEL.equals(verification.getChannel())) {
      log.info("Controlled verification challenge issued over TOTP");
      return;
    }
    if (EMAIL_CHANNEL.equals(verification.getChannel())) {
      try {
        emailChannel.deliver(transaction.getUser().getEmail(), code, ttlMinutes);
      } catch (RuntimeException unavailable) {
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Email verification delivery failed; no challenge was exposed");
      }
      return;
    }
    try {
      channel.deliver(transaction.getId(), code);
    } catch (IllegalStateException unavailable) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Verification delivery is not configured; no challenge was exposed");
    }
    log.info("Controlled verification challenge issued (simulated out-of-band channel)");
    if (logCode) {
      log.warn(
          "[DEV ONLY] Simulated verification code for payment {}: {} (expires in {} minutes)",
          transaction.getId(),
          code,
          ttlMinutes);
    }
  }

  @Transactional
  public VerificationConfirmResponseDto confirm(UUID paymentId, String code) {
    return confirm(paymentId, code, null);
  }

  @Transactional
  public VerificationConfirmResponseDto confirmForUser(
      UUID paymentId, String code, UUID userId) {
    return confirm(paymentId, code, userId);
  }

  private VerificationConfirmResponseDto confirm(
      UUID paymentId, String code, UUID expectedUserId) {
    Transaction transaction =
        transactions
            .findByIdForUpdate(paymentId)
            .filter(
                candidate ->
                    expectedUserId == null
                        || (candidate.getUser() != null
                            && expectedUserId.equals(candidate.getUser().getId())))
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
    String channel = verification.getChannel();
    boolean simulated = channel == null || SIMULATED_CHANNEL.equals(channel);
    String hint =
        switch (channel == null ? SIMULATED_CHANNEL : channel) {
          case TOTP_CHANNEL -> "Enter the 6-digit code from your authenticator app.";
          case EMAIL_CHANNEL -> "Enter the 6-digit code sent to the account email.";
          default ->
              "Ask the developer to read the code from the server-side dev outbox; "
                  + "it is never returned to the browser.";
        };
    return new GuardedVerificationDto(
        verification.getId(),
        verification.getStatus(),
        verification.getExpiresAt(),
        Math.max(0, verification.getMaxAttempts() - verification.getAttempts()),
        simulated,
        hint);
  }

  private boolean codeMatches(PaymentVerification verification, Transaction transaction, String code) {
    if (code == null) {
      return false;
    }
    if (TOTP_CHANNEL.equals(verification.getChannel())) {
      if (mfa == null || totp == null) {
        return false;
      }
      Optional<String> secret = mfa.activeSecret();
      return secret.isPresent() && totp.verify(secret.get(), code, Instant.now());
    }
    return MessageDigest.isEqual(
        verification.getChallengeCodeHash().getBytes(StandardCharsets.UTF_8),
        hash(code.trim(), transaction.getId()).getBytes(StandardCharsets.UTF_8));
  }

  private static String challengeCode() {
    return DEMO_VERIFICATION_CODE;
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
