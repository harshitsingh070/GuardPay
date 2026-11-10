package com.guardpay.intelligence.service;

import com.guardpay.intelligence.domain.ReputationStatus;
import java.util.UUID;

/** Read-only reputation lookup used by the payment evaluation pipeline. */
public interface RecipientReputationLookup {

  /**
   * Resolves reputation for one owner-scoped recipient. Never throws for
   * missing data; implementations must fail safe with
   * {@link RecipientReputationEvidence#unavailable()}.
   */
  RecipientReputationEvidence lookup(UUID userId, UUID recipientId);
}
