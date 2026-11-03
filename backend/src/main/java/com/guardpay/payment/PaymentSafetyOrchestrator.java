package com.guardpay.payment;

import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.signal.AutomaticRiskEvaluationService;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single guarded-payment flow: resolve identities (learning unknown payee
 * names into the catalog so history semantics stay real) â†’ full automatic
 * pipeline (history, device, AI, engine) â†’ transactional completion.
 *
 * <p>Risk evaluation happens outside any database transaction so a slow AI
 * call never holds a connection or a row lock. Scoring stays in the engine,
 * PayPal protocol in {@link PayPalOrderService}, and persistence in
 * {@link GuardedPaymentCompletionService}.
 */
@Service
public class PaymentSafetyOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(PaymentSafetyOrchestrator.class);

  private final AutomaticRiskEvaluationService automaticRisk;
  private final GuardedPaymentCompletionService completion;
  private final DemoUserService demoUser;
  private final RecipientRepository recipients;
  private final MerchantRepository merchants;
  private final GuardedPaymentIdempotencyRepository idempotency;

  public PaymentSafetyOrchestrator(
      AutomaticRiskEvaluationService automaticRisk,
      GuardedPaymentCompletionService completion,
      DemoUserService demoUser,
      RecipientRepository recipients,
      MerchantRepository merchants,
      GuardedPaymentIdempotencyRepository idempotency) {
    this.automaticRisk = automaticRisk;
    this.completion = completion;
    this.demoUser = demoUser;
    this.recipients = recipients;
    this.merchants = merchants;
    this.idempotency = idempotency;
  }

  /**
   * Guarded initiation for the fixed demo account. A repeated key returns the
   * stored result verbatim; a reused key with different content is rejected.
   * The completion step locks the key before side effects.
   */
  public GuardedPaymentResponseDto initiate(
      GuardedPaymentRequestDto dto, String idempotencyKey) {
    String key = normalizeKey(idempotencyKey);
    if (key != null) {
      Optional<GuardedPaymentIdempotency> existing =
          idempotency.findByIdempotencyKey(key);
      if (existing.isPresent()) {
        return GuardedPaymentReplay.replayOrConflict(existing.get(), dto);
      }
    }

    BigDecimal amount = dto.getAmount().setScale(2, RoundingMode.HALF_UP);
    User user = demoUser.currentUser();
    Recipient recipient =
        resolveRecipient(
            user,
            dto.getRecipientId(),
            dto.getRecipientName(),
            dto.getRecipientEmail(),
            dto.getRecipientPaypalAccountId());
    if (recipient == null) {
      // A payment with no counterparty is not a meaningful PayPal destination
      // and would otherwise let every identity-based signal silently vanish.
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "recipientName or recipientId is required");
    }
    Merchant merchant = resolveMerchant(dto.getMerchantId(), dto.getMerchantName());

    AutomaticEvaluationRequestDto auto = new AutomaticEvaluationRequestDto();
    auto.setAmount(amount);
    auto.setCurrency(dto.getCurrency());
    auto.setRecipientId(recipient != null ? recipient.getId() : null);
    auto.setMerchantId(merchant != null ? merchant.getId() : null);
    auto.setDeviceId(dto.getDeviceId());
    auto.setContentText(dto.getContentText());
    auto.setContentUrl(dto.getContentUrl());
    AutomaticEvaluationResponseDto risk = automaticRisk.evaluate(auto);
    if (risk.recommendedAction() != com.guardpay.risk.RecommendedAction.STOP
        && blankToNull(recipient.getEmail()) == null
        && blankToNull(recipient.getPaypalAccountId()) == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "recipientEmail or recipientPaypalAccountId is required for a PayPal destination");
    }

    log.debug("Guarded payment evaluated (action={})", risk.recommendedAction());
    return completion.complete(dto, key, amount, user, recipient, merchant, risk);
  }

  private static String normalizeKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return null;
    }
    String key = idempotencyKey.trim();
    if (key.length() > 128) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Idempotency key must be 128 characters or fewer");
    }
    return key;
  }

  private Recipient resolveRecipient(
      User user,
      UUID recipientId,
      String recipientName,
      String recipientEmail,
      String recipientPaypalAccountId) {
    String email = blankToNull(recipientEmail);
    String paypalAccount = blankToNull(recipientPaypalAccountId);
    if (recipientId != null) {
      Recipient existing =
          recipients
              .findByIdAndUserId(recipientId, user.getId())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient"));
      if (email != null) {
        existing.setEmail(email.toLowerCase(java.util.Locale.ROOT));
      }
      if (paypalAccount != null) {
        existing.setPaypalAccountId(paypalAccount);
      }
      return recipients.save(existing);
    }
    String name = blankToNull(recipientName);
    if (name == null && email == null) {
      return null;
    }
    Optional<Recipient> known =
        email != null
            ? recipients.findByUserIdAndEmailIgnoreCase(user.getId(), email)
            : recipients.findByUserIdAndNameIgnoreCase(user.getId(), name);
    return known
        .map(
            recipient -> {
              boolean changed = false;
              if (email != null
                  && !email.equalsIgnoreCase(recipient.getEmail())) {
                recipient.setEmail(email.toLowerCase(java.util.Locale.ROOT));
                changed = true;
              }
              if (paypalAccount != null
                  && !paypalAccount.equals(recipient.getPaypalAccountId())) {
                recipient.setPaypalAccountId(paypalAccount);
                changed = true;
              }
              return changed ? recipients.save(recipient) : recipient;
            })
        .orElseGet(
            () -> {
              Recipient fresh = new Recipient();
              fresh.setUser(user);
              fresh.setName(name != null ? name : email);
              fresh.setEmail(email == null ? null : email.toLowerCase(java.util.Locale.ROOT));
              fresh.setPaypalAccountId(paypalAccount);
              return recipients.save(fresh);
            });
  }

  private Merchant resolveMerchant(UUID merchantId, String merchantName) {
    if (merchantId != null) {
      return merchants
          .findById(merchantId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown merchant"));
    }
    String name = blankToNull(merchantName);
    if (name == null) {
      return null;
    }
    return merchants
        .findByNameIgnoreCase(name)
        .orElseGet(
            () -> {
              Merchant fresh = new Merchant();
              fresh.setName(name);
              return merchants.save(fresh);
            });
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
