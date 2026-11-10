package com.guardpay.risk.correlation;

import java.util.List;

/**
 * One evidence slot in a correlation rule. The slot is satisfied by the
 * strongest available signal among {@code codes}; the {@code family} is fixed
 * so overlap and independence math stays deterministic even when a signal is
 * absent.
 */
public record SignalRequirement(EvidenceFamily family, List<String> codes) {

  public SignalRequirement {
    codes = List.copyOf(codes);
  }
}
