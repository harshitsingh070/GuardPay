package com.guardpay.evaluation;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.util.List;

public record BenchmarkResult(
    String scenarioId,
    String category,
    BenchmarkLabel label,
    BenchmarkMode mode,
    int expectedScore,
    RiskLevel expectedLevel,
    RecommendedAction expectedAction,
    List<String> expectedSignals,
    Integer actualScore,
    RiskLevel actualLevel,
    RecommendedAction actualAction,
    List<String> actualSignals,
    String aiStatus,
    long latencyNanos,
    boolean scoreMatch,
    boolean levelMatch,
    boolean actionMatch,
    boolean signalsMatch,
    String error) {

  public BenchmarkResult {
    expectedSignals = expectedSignals == null ? List.of() : List.copyOf(expectedSignals);
    actualSignals = actualSignals == null ? List.of() : List.copyOf(actualSignals);
  }

  public boolean evaluated() {
    return error == null && actualAction != null;
  }
}
