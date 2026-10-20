package com.guardpay.payment;

import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.VerificationConfirmRequestDto;
import com.guardpay.payment.dto.VerificationConfirmResponseDto;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guarded payments (development stage — no authentication yet).
 *
 * <p>Accepts payment/business context only. Risk is derived server-side;
 * nothing here can force a decision. Thin by design.
 */
@RestController
@RequestMapping("/api/payments")
public class GuardedPaymentController {

  private final PaymentSafetyOrchestrator orchestrator;
  private final PaymentVerificationService verificationService;

  public GuardedPaymentController(
      PaymentSafetyOrchestrator orchestrator, PaymentVerificationService verificationService) {
    this.orchestrator = orchestrator;
    this.verificationService = verificationService;
  }

  @PostMapping("/guarded")
  public ResponseEntity<GuardedPaymentResponseDto> initiate(
      @Valid @RequestBody GuardedPaymentRequestDto request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ResponseEntity.ok(orchestrator.initiate(request, idempotencyKey));
  }

  @PostMapping("/{paymentId}/verification/confirm")
  public ResponseEntity<VerificationConfirmResponseDto> confirmVerification(
      @PathVariable UUID paymentId, @Valid @RequestBody VerificationConfirmRequestDto request) {
    return ResponseEntity.ok(verificationService.confirm(paymentId, request.code()));
  }
}
