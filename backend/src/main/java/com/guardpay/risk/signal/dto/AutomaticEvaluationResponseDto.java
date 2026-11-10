package com.guardpay.risk.signal.dto;

import com.guardpay.intelligence.dto.RecipientReputationDto;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.correlation.dto.CorrelationFindingDto;
import com.guardpay.risk.dto.RiskFindingDto;
import java.util.List;

/**
 * Response for {@code POST /api/risk/evaluate-automatic}: engine decision
 * plus the backend-derived evidence behind it. Nothing here was supplied by
 * the client as a risk verdict. {@code aiStatus} is one of
 * {@code NOT_REQUESTED}, {@code AVAILABLE}, {@code UNAVAILABLE}.
 * {@code behavioralProfileStatus} is one of {@code AVAILABLE}, {@code INSUFFICIENT_DATA}, {@code UNAVAILABLE}.
 * {@code correlationStatus} is one of {@code AVAILABLE}, {@code NO_MATCH},
 * {@code NO_SIGNALS}, {@code UNAVAILABLE}.
 */
public record AutomaticEvaluationResponseDto(
    int score,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction,
    String explanation,
    List<PaymentSignalDto> automaticSignals,
    List<RiskFindingDto> findings,
    String aiStatus,
    String behavioralProfileStatus,
    List<CorrelationFindingDto> correlationFindings,
    String correlationStatus,
    RecipientReputationDto recipientReputation) {

  public AutomaticEvaluationResponseDto {
    correlationFindings =
        correlationFindings == null ? List.of() : List.copyOf(correlationFindings);
  }

  /** Pre-reputation contract for callers that do not run the intelligence layer. */
  public AutomaticEvaluationResponseDto(
      int score,
      RiskLevel riskLevel,
      RecommendedAction recommendedAction,
      String explanation,
      List<PaymentSignalDto> automaticSignals,
      List<RiskFindingDto> findings,
      String aiStatus,
      String behavioralProfileStatus,
      List<CorrelationFindingDto> correlationFindings,
      String correlationStatus) {
    this(
        score,
        riskLevel,
        recommendedAction,
        explanation,
        automaticSignals,
        findings,
        aiStatus,
        behavioralProfileStatus,
        correlationFindings,
        correlationStatus,
        null);
  }

  /** Pre-correlation contract for callers that do not run the correlation layer. */
  public AutomaticEvaluationResponseDto(
      int score,
      RiskLevel riskLevel,
      RecommendedAction recommendedAction,
      String explanation,
      List<PaymentSignalDto> automaticSignals,
      List<RiskFindingDto> findings,
      String aiStatus,
      String behavioralProfileStatus) {
    this(
        score,
        riskLevel,
        recommendedAction,
        explanation,
        automaticSignals,
        findings,
        aiStatus,
        behavioralProfileStatus,
        List.of(),
        "UNAVAILABLE");
  }
}
