package com.guardpay.paypal.dto;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.math.BigDecimal;

/**
 * API response for {@code POST /api/paypal/orders}. For non-ALLOW decisions
 * no PayPal order is created: {@code orderId}/{@code approvalUrl} are null,
 * {@code allowed} is false, and {@code message} explains why.
 */
public record CreateOrderResponseDto(
    String orderId,
    String status,
    String approvalUrl,
    BigDecimal amount,
    String currency,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction,
    boolean allowed,
    String message) {}
