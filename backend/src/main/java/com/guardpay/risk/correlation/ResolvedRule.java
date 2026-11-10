package com.guardpay.risk.correlation;

import java.util.List;

/** A rule match after overlap-aware discounting. */
record ResolvedRule(
    RuleMatch match, double interaction, double overlapFactor, boolean overlapDiscounted) {

  List<CorrelationSignal> participants() {
    return match.participants();
  }
}
