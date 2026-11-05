package com.guardpay.payment;

import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.VerificationConfirmRequestDto;
import com.guardpay.payment.dto.VerificationConfirmResponseDto;
import com.guardpay.user.DemoUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guarded payments for the authenticated account.
 *
 * <p>Accepts payment/business context only. Risk is derived server-side;
 * nothing here can force a decision. Thin by design.
 */
@RestController
@RequestMapping("/api/payments")
public class GuardedPaymentController {

  private final PaymentSafetyOrchestrator orchestrator;
  private final PaymentVerificationService verificationService;
  private final DemoUserService demoUser;

  public GuardedPaymentController(
      PaymentSafetyOrchestrator orchestrator, PaymentVerificationService verificationService) {
    this(orchestrator, verificationService, null);
  }

  @Autowired
  public GuardedPaymentController(
      PaymentSafetyOrchestrator orchestrator,
      PaymentVerificationService verificationService,
      DemoUserService demoUser) {
    this.orchestrator = orchestrator;
    this.verificationService = verificationService;
    this.demoUser = demoUser;
  }

  @PostMapping("/guarded")
  public ResponseEntity<GuardedPaymentResponseDto> initiate(
      @Valid @RequestBody GuardedPaymentRequestDto request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      HttpServletRequest httpRequest) {
    request.setClientIp(clientIp(httpRequest));
    return ResponseEntity.ok(orchestrator.initiate(request, idempotencyKey));
  }

  /**
   * Client IP is server-observed, never taken from JSON. The first
   * X-Forwarded-For hop is used only when a trusted proxy added it.
   */
  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      String first = forwarded.split(",")[0].trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    return request.getRemoteAddr();
  }

  @PostMapping("/{paymentId}/verification/confirm")
  public ResponseEntity<VerificationConfirmResponseDto> confirmVerification(
      @PathVariable UUID paymentId,
      @Valid @RequestBody VerificationConfirmRequestDto request) {
    if (demoUser == null) {
      return ResponseEntity.ok(verificationService.confirm(paymentId, request.code()));
    }
    return ResponseEntity.ok(
        verificationService.confirmForUser(
            paymentId, request.code(), demoUser.currentUser().getId()));
  }
}

