package com.guardpay.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guardpay.payment.GuardedPaymentStatus;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.dto.PaymentSignalDto;
import java.util.List;
import java.util.UUID;

/**
 * Guarded-payment response: stored payment id, engine risk block, outcome
 * state, and — only for ALLOW — the PayPal approval to continue with.
 * Never carries credentials, tokens, or client decisions.
 */
public record GuardedPaymentResponseDto(
    UUID paymentId,
    GuardedRiskDto risk,
    GuardedPaymentStatus paymentStatus,
    @JsonInclude(JsonInclude.Include.NON_NULL) GuardedPaypalOrderDto paypalOrder,
    @JsonInclude(JsonInclude.Include.NON_NULL) GuardedVerificationDto verification) {

  public record GuardedRiskDto(
      int score,
      RiskLevel riskLevel,
      RecommendedAction recommendedAction,
      String explanation,
      List<PaymentSignalDto> automaticSignals,
      List<RiskFindingDto> findings,
      String aiStatus,
      String behavioralProfileStatus) {}

  public record GuardedPaypalOrderDto(String orderId, String approvalUrl) {}
}
