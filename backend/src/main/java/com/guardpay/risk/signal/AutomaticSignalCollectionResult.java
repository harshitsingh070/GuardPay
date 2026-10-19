package com.guardpay.risk.signal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evidence gathered by the collector — no score, no decision. Carries the
 * computed historical average so the engine can apply its own large-amount
 * rule to the same numbers (single definition, shared constant).
 */
public record AutomaticSignalCollectionResult(List<PaymentSignal> signals, BigDecimal historicalAverage) {

  /** Whether the given signal fired. */
  public boolean detected(PaymentSignalType type) {
    return signals.stream().anyMatch(signal -> signal.type() == type && signal.detected());
  }
}
