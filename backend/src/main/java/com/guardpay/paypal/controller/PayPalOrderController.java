package com.guardpay.paypal.controller;

import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.dto.CreateOrderRequestDto;
import com.guardpay.paypal.dto.CreateOrderResponseDto;
import com.guardpay.paypal.service.PayPalOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayPal Orders endpoints (development stage — no authentication yet; the auth
 * milestone will protect them). Thin by design: validate, delegate, respond.
 * Capture permission always comes from the stored server-side risk decision.
 */
@RestController
@RequestMapping("/api/paypal/orders")
public class PayPalOrderController {

  private final PayPalOrderService service;

  public PayPalOrderController(PayPalOrderService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<CreateOrderResponseDto> createOrder(
      @Valid @RequestBody CreateOrderRequestDto request) {
    return ResponseEntity.ok(service.createOrder(request));
  }

  @PostMapping("/{orderId}/capture")
  public ResponseEntity<CaptureOrderResponseDto> captureOrder(@PathVariable String orderId) {
    return ResponseEntity.ok(service.captureOrder(orderId));
  }
}
