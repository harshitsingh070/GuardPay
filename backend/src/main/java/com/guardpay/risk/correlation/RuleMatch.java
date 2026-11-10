package com.guardpay.risk.correlation;

import java.util.List;

/** A rule whose requirements were all satisfied, before overlap resolution. */
record RuleMatch(
    CorrelationRule rule,
    List<CorrelationSignal> participants,
    double interaction,
    List<String> unavailableSignals) {

  RuleMatch {
    participants = List.copyOf(participants);
    unavailableSignals = List.copyOf(unavailableSignals);
  }
}
