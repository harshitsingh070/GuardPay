package com.guardpay.paypal.dto;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;

/**
 * Returned with HTTP 403 when capture is blocked by the server-side risk
 * decision. The frontend can never override this.
 */
public record OrderGateResponseDto(
    boolean allowed, RecommendedAction action, RiskLevel riskLevel, String message, String orderId) {}
