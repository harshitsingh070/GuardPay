package com.guardpay.paypal.exception;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;

/**
 * Capture refused because the stored server-side risk decision is not ALLOW.
 * Mapped to HTTP 403 with a safe gate body — never a stack trace.
 */
public class CaptureBlockedException extends RuntimeException {

  private final RecommendedAction action;
  private final RiskLevel riskLevel;
  private final String orderId;

  public CaptureBlockedException(
      String message, RecommendedAction action, RiskLevel riskLevel, String orderId) {
    super(message);
    this.action = action;
    this.riskLevel = riskLevel;
    this.orderId = orderId;
  }

  public RecommendedAction getAction() {
    return action;
  }

  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public String getOrderId() {
    return orderId;
  }
}
