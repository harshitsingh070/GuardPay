package com.guardpay.paypal.controller;

import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.service.PayPalOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayPal capture endpoint. Order creation is deliberately NOT exposed here:
 * every new order must pass through the guarded-payment orchestrator so the
 * server, never the client, decides whether an order may be created. Capture
 * permission always comes from the stored server-side risk decision.
 */
@RestController
@RequestMapping("/api/paypal/orders")
public class PayPalOrderController {

  private final PayPalOrderService service;

  public PayPalOrderController(PayPalOrderService service) {
    this.service = service;
  }

  @PostMapping("/{orderId}/capture")
  public ResponseEntity<CaptureOrderResponseDto> captureOrder(@PathVariable String orderId) {
    return ResponseEntity.ok(service.captureOrder(orderId));
  }
}
