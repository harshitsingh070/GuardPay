package com.guardpay.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.risk.correlation.dto.CorrelationFindingDto;
import com.guardpay.transaction.Transaction;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence for engine results. Kept separate from
 * {@link DeterministicRiskEngine}, which never touches repositories -
 * evaluations are calculated in memory and saved only when explicitly asked.
 */
@Service
public class RiskAssessmentService {

  private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private final RiskAssessmentRepository repository;

  public RiskAssessmentService(RiskAssessmentRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public RiskAssessment saveAssessment(Transaction transaction, RiskEvaluationResult result) {
    RiskAssessment assessment = new RiskAssessment();
    assessment.setTransaction(transaction);
    assessment.setScore(result.score());
    assessment.setRiskLevel(result.riskLevel());
    assessment.setRecommendedAction(result.recommendedAction());
    assessment.setExplanation(result.explanation());
    assessment.setCorrelationContribution(result.correlationContribution());
    assessment.setCorrelationFindings(serializeCorrelation(result));
    assessment.setCorrelationStatus(
        result.correlationFindings().isEmpty()
            ? "NO_MATCH"
            : com.guardpay.risk.correlation.CorrelationResult.AVAILABLE);
    return repository.save(assessment);
  }

  /** Correlation is audit data: if it cannot be serialized, the decision still saves. */
  private static String serializeCorrelation(RiskEvaluationResult result) {
    if (result.correlationFindings().isEmpty()) {
      return null;
    }
    try {
      List<CorrelationFindingDto> dtos =
          result.correlationFindings().stream().map(CorrelationFindingDto::from).toList();
      return JSON.writeValueAsString(dtos);
    } catch (JsonProcessingException notSerializable) {
      log.warn("Correlation findings could not be serialized; storing none");
      return null;
    }
  }
}
