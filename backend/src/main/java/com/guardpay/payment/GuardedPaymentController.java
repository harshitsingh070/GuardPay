package com.guardpay.payment;

import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

  public GuardedPaymentController(PaymentSafetyOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

  @PostMapping("/guarded")
  public ResponseEntity<GuardedPaymentResponseDto> initiate(
      @Valid @RequestBody GuardedPaymentRequestDto request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ResponseEntity.ok(orchestrator.initiate(request, idempotencyKey));
  }
}
