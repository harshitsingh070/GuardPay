package com.guardpay.intelligence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes shared recipient reputation so time decay and expiry
 * are applied even when no new evidence arrives.
 */
@Component
public class RecipientIntelligenceMaintenanceJob {

  private static final Logger log =
      LoggerFactory.getLogger(RecipientIntelligenceMaintenanceJob.class);

  private final RecipientReputationService reputation;

  public RecipientIntelligenceMaintenanceJob(RecipientReputationService reputation) {
    this.reputation = reputation;
  }

  @Scheduled(fixedDelayString = "${guardpay.intelligence.maintenance-interval-ms:3600000}")
  public void refreshStaleRecipients() {
    try {
      int refreshed = reputation.refreshStaleEntities();
      if (refreshed > 0) {
        log.info("Recipient reputation maintenance refreshed {} entities", refreshed);
      }
    } catch (RuntimeException failure) {
      log.warn("Recipient reputation maintenance failed; will retry next interval", failure);
    }
  }
}
