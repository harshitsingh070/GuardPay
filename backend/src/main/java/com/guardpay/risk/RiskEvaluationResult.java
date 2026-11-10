package com.guardpay.risk;

import com.guardpay.risk.correlation.CorrelationFinding;
import java.util.List;

/**
 * Immutable engine output: score (0-100 heuristic, NOT a probability),
 * level, deterministic action, per-signal findings, and a user-readable
 * explanation generated without any LLM.
 *
 * <p>{@code correlationFindings} and {@code correlationContribution} are the
 * bounded, already de-duplicated contribution of the correlation layer.
 * {@code recipientReputationContribution} is the bounded global-reputation
 * contribution; it is included in the score but kept separate for audit.
 * The shorter constructors preserve earlier contracts for callers that do not
 * run those layers.
 */
public record RiskEvaluationResult(
    int score,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction,
    List<RiskFinding> findings,
    String explanation,
    List<CorrelationFinding> correlationFindings,
    int correlationContribution,
    int recipientReputationContribution) {

  public RiskEvaluationResult {
    correlationFindings =
        correlationFindings == null ? List.of() : List.copyOf(correlationFindings);
  }

  public RiskEvaluationResult(
      int score,
      RiskLevel riskLevel,
      RecommendedAction recommendedAction,
      List<RiskFinding> findings,
      String explanation,
      List<CorrelationFinding> correlationFindings,
      int correlationContribution) {
    this(
        score,
        riskLevel,
        recommendedAction,
        findings,
        explanation,
        correlationFindings,
        correlationContribution,
        0);
  }

  public RiskEvaluationResult(
      int score,
      RiskLevel riskLevel,
      RecommendedAction recommendedAction,
      List<RiskFinding> findings,
      String explanation) {
    this(score, riskLevel, recommendedAction, findings, explanation, List.of(), 0, 0);
  }
}
