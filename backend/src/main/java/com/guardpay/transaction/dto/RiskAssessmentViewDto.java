package com.guardpay.transaction.dto;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.dto.RiskFindingDto;
import java.time.OffsetDateTime;
import java.util.List;

/** The stored risk decision behind a transaction, including why it was made. */
public record RiskAssessmentViewDto(
    int score,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction,
    String explanation,
    List<RiskFindingDto> findings,
    OffsetDateTime createdAt) {}
