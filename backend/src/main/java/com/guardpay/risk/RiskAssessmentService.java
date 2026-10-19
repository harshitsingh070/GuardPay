package com.guardpay.risk;

import com.guardpay.transaction.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence for engine results. Kept separate from
 * {@link DeterministicRiskEngine}, which never touches repositories —
 * evaluations are calculated in memory and saved only when explicitly asked.
 */
@Service
public class RiskAssessmentService {

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
    return repository.save(assessment);
  }
}
