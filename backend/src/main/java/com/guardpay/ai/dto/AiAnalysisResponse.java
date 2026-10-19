package com.guardpay.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Structured AI output: evidence with confidence, never a payment decision.
 * Confidence is observation certainty, NOT a risk score.
 */
public record AiAnalysisResponse(UUID analysisId, List<AiFindingDto> findings, String summary) {}
