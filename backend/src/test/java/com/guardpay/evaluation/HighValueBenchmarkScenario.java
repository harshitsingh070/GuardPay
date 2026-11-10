package com.guardpay.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * A high-value benchmark case. Older history is distributed across the
 * long-term window; recent amounts are placed inside the 7-day escalation
 * window. {@code newRecipient} selects whether the current payment goes to a
 * recipient with no completed history.
 */
public record HighValueBenchmarkScenario(
    String id,
    String category,
    String description,
    boolean expectedHighValue,
    Input input) {

  public record Input(
      BigDecimal amount,
      String currency,
      String historyCurrency,
      List<BigDecimal> historyAmounts,
      List<BigDecimal> recentAmounts,
      boolean newRecipient) {}
}
