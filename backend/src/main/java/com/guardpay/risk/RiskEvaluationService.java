package com.guardpay.risk;

import com.guardpay.risk.dto.RiskEvaluationRequestDto;
import com.guardpay.risk.dto.RiskEvaluationResponseDto;
import com.guardpay.risk.dto.RiskFindingDto;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * API-facing risk service: DTO → engine input → {@link DeterministicRiskEngine}
 * → response DTO. Holds no risk rules itself; the engine stays independent of
 * HTTP. Nothing is persisted here (see {@link RiskAssessmentService}).
 */
@Service
public class RiskEvaluationService {

  private final DeterministicRiskEngine engine;

  public RiskEvaluationService(DeterministicRiskEngine engine) {
    this.engine = engine;
  }

  public RiskEvaluationResponseDto evaluate(RiskEvaluationRequestDto dto) {
    RiskEvaluationRequest request =
        new RiskEvaluationRequest(
            dto.getAmount(),
            dto.getHistoricalAverage(),
            dto.isActiveCall(),
            dto.isNewRecipient(),
            dto.isUnknownMerchant(),
            dto.isUnexpectedInvoice(),
            dto.isUrgentLanguage(),
            dto.isSuspiciousUrl(),
            dto.isSocialEngineeringDetected());

    RiskEvaluationResult result = engine.evaluate(request);

    List<RiskFindingDto> findings =
        result.findings().stream()
            .map(
                finding ->
                    new RiskFindingDto(
                        finding.code(),
                        finding.title(),
                        finding.description(),
                        finding.points(),
                        finding.triggered()))
            .toList();

    return new RiskEvaluationResponseDto(
        result.score(),
        result.riskLevel(),
        result.recommendedAction(),
        result.explanation(),
        findings);
  }
}
