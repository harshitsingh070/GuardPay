package com.guardpay.risk;

import java.util.List;

/**
 * Immutable engine output: score (0–100 heuristic, NOT a probability),
 * level, deterministic action, per-signal findings, and a user-readable
 * explanation generated without any LLM.
 */
public record RiskEvaluationResult(
    int score,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction,
    List<RiskFinding> findings,
    String explanation) {}
