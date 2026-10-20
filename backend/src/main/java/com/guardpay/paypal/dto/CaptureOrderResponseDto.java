package com.guardpay.paypal.dto;

import com.guardpay.payment.GuardedPaymentStatus;
import java.math.BigDecimal;

/** API response for a permitted {@code POST /api/paypal/orders/{orderId}/capture}. */
public record CaptureOrderResponseDto(
    String orderId,
    String status,
    String captureId,
    BigDecimal amount,
    String currency,
    GuardedPaymentStatus paymentStatus) {}
