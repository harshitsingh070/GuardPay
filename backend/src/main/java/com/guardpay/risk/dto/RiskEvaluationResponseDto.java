package com.guardpay.risk.dto;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.util.List;

/**
 * API response for {@code POST /api/risk/evaluate}: score (0–100 heuristic,
 * NOT a probability), level, deterministic action, explanation, and all
 * findings in the engine's stable order.
 */
public record RiskEvaluationResponseDto(
    int score,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction,
    String explanation,
    List<RiskFindingDto> findings) {}
