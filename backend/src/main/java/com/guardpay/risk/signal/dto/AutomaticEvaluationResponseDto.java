package com.guardpay.risk.signal.dto;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.dto.RiskFindingDto;
import java.util.List;

/**
 * Response for {@code POST /api/risk/evaluate-automatic}: engine decision
 * plus the backend-derived evidence behind it. Nothing here was supplied by
 * the client as a risk verdict. {@code aiStatus} is one of
 * {@code NOT_REQUESTED}, {@code AVAILABLE}, {@code UNAVAILABLE}.
 */
public record AutomaticEvaluationResponseDto(
    int score,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction,
    String explanation,
    List<PaymentSignalDto> automaticSignals,
    List<RiskFindingDto> findings,
    String aiStatus) {}
