package com.guardpay.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * Structured AI output: evidence with confidence, never a payment decision.
 * Confidence is observation certainty, NOT a risk score. The wire contract is
 * snake_case; {@code analysisId} maps from {@code analysis_id}.
 */
public record AiAnalysisResponse(
    @JsonProperty("analysis_id") UUID analysisId,
    List<AiFindingDto> findings,
    String summary) {}
