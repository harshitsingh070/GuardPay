package com.guardpay.paypal.controller;

import com.guardpay.paypal.dto.PayPalHealthResponse;
import com.guardpay.paypal.service.PayPalConnectionStatus;
import com.guardpay.paypal.service.PayPalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development-stage PayPal probe (no authentication yet — the auth milestone
 * will protect it). Thin by design: delegates and maps, nothing else.
 */
@RestController
@RequestMapping("/api/paypal")
public class PayPalController {

  private final PayPalService service;

  public PayPalController(PayPalService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public ResponseEntity<PayPalHealthResponse> health() {
    PayPalConnectionStatus status = service.checkConnection();
    return ResponseEntity.ok(new PayPalHealthResponse(status.status(), "PAYPAL", status.environment()));
  }
}
