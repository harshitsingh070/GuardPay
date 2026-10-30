package com.guardpay.evaluation;

import com.guardpay.risk.DeterministicRiskEngine;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RecipientMetrics(
    int recipientScenarios,
    int newRecipientScenarios,
    int newRecipientDetected,
    int knownRecipientScenarios,
    int knownRecipientFalseNew,
    int amountAnomalyScenarios,
    int amountAnomalyDetected,
    int repeatedRecipientAttemptScenarios,
    int repeatedRecipientAttemptDetected,
    int sufficientHistoryScenarios,
    int insufficientHistoryScenarios,
    Double newRecipientDetectionRate,
    Double amountAnomalyDetectionRate,
    Double repeatedRecipientAttemptDetectionRate,
    Double historyCoverageRate) {

  public static RecipientMetrics from(
      List<BenchmarkScenario> scenarios, List<BenchmarkResult> results) {
    Map<String, BenchmarkResult> resultsById = new LinkedHashMap<>();
    for (BenchmarkResult result : results) {
      if (result.evaluated()) {
        resultsById.put(result.scenarioId(), result);
      }
    }

    int recipientScenarios = 0;
    int newScenarios = 0;
    int newDetected = 0;
    int knownScenarios = 0;
    int knownFalseNew = 0;
    int anomalyScenarios = 0;
    int anomalyDetected = 0;
    int repeatedScenarios = 0;
    int repeatedDetected = 0;
    int sufficientHistory = 0;
    int insufficientHistory = 0;

    for (BenchmarkScenario scenario : scenarios) {
      BenchmarkScenario.Input input = scenario.input();
      if (input.recipient() == CounterpartyState.NONE) {
        continue;
      }
      recipientScenarios++;
      BenchmarkResult result = resultsById.get(scenario.id());
      List<String> actualSignals = result == null ? List.of() : result.actualSignals();

      List<BigDecimal> history = recipientHistory(input);
      if (history.size() >= 3) {
        sufficientHistory++;
      } else {
        insufficientHistory++;
      }

      if (input.recipient() == CounterpartyState.NEW) {
        newScenarios++;
        if (actualSignals.contains("NEW_RECIPIENT")) {
          newDetected++;
        }
      } else {
        knownScenarios++;
        if (actualSignals.contains("NEW_RECIPIENT")) {
          knownFalseNew++;
        }
      }

      if (result != null && result.expectedSignals().contains("RECIPIENT_AMOUNT_ANOMALY")) {
        anomalyScenarios++;
        if (actualSignals.contains("RECIPIENT_AMOUNT_ANOMALY")) {
          anomalyDetected++;
        }
      }

      if (input.recentAttempts() + 1 >= DeterministicRiskEngine.REPEATED_ATTEMPT_THRESHOLD) {
        repeatedScenarios++;
        if (actualSignals.contains("REPEATED_ATTEMPTS")) {
          repeatedDetected++;
        }
      }
    }

    return new RecipientMetrics(
        recipientScenarios,
        newScenarios,
        newDetected,
        knownScenarios,
        knownFalseNew,
        anomalyScenarios,
        anomalyDetected,
        repeatedScenarios,
        repeatedDetected,
        sufficientHistory,
        insufficientHistory,
        MetricMath.ratio(newDetected, newScenarios),
        MetricMath.ratio(anomalyDetected, anomalyScenarios),
        MetricMath.ratio(repeatedDetected, repeatedScenarios),
        MetricMath.ratio(sufficientHistory, recipientScenarios));
  }

  private static List<BigDecimal> recipientHistory(BenchmarkScenario.Input input) {
    if (input.recipient() != CounterpartyState.KNOWN) {
      return List.of();
    }
    return input.recipientHistoryAmounts() == null
        ? input.historyAmounts()
        : input.recipientHistoryAmounts();
  }
}
