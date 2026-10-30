package com.guardpay.evaluation;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.math.BigDecimal;
import java.util.List;

public record BenchmarkScenario(
    String id,
    String category,
    String description,
    BenchmarkLabel label,
    Integer expectedScore,
    RiskLevel expectedRiskLevel,
    RecommendedAction expectedAction,
    List<String> expectedSignals,
    Input input,
    List<String> tags,
    ExpectedAi expectedAi) {

  public record Input(
      BigDecimal amount,
      String currency,
      List<BigDecimal> historyAmounts,
      List<BigDecimal> recipientHistoryAmounts,
      Integer recentAttempts,
      CounterpartyState recipient,
      CounterpartyState merchant,
      String contentText,
      String contentUrl,
      DeviceState device,
      List<String> aiFindings) {}

  public record ExpectedAi(
      Integer score,
      RiskLevel riskLevel,
      RecommendedAction action,
      List<String> signals) {}
}
