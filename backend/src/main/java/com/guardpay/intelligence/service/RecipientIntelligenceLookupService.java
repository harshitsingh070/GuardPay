package com.guardpay.intelligence.service;

import com.guardpay.intelligence.config.RecipientIntelligenceProperties;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.MatchStatus;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.service.RecipientIdentityService.IdentityMatch;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only reputation lookup for the payment pipeline.
 *
 * <p>Never writes on the evaluation path. A missing recipient, an unconfigured
 * HMAC key, or any repository failure returns {@code UNAVAILABLE}; an entity
 * with no evidence returns {@code NO_DATA}. Neither is treated as "clean".
 */
@Service
public class RecipientIntelligenceLookupService implements RecipientReputationLookup {

  private static final Logger log =
      LoggerFactory.getLogger(RecipientIntelligenceLookupService.class);

  private final RecipientRepository recipients;
  private final RecipientIdentityService identity;
  private final RecipientIntelligenceProperties properties;

  public RecipientIntelligenceLookupService(
      RecipientRepository recipients,
      RecipientIdentityService identity,
      RecipientIntelligenceProperties properties) {
    this.recipients = recipients;
    this.identity = identity;
    this.properties = properties;
  }

  @Override
  @Transactional(readOnly = true)
  public RecipientReputationEvidence lookup(UUID userId, UUID recipientId) {
    if (userId == null
        || recipientId == null
        || !properties.isEnabled()
        || !identity.isConfigured()) {
      return RecipientReputationEvidence.unavailable();
    }
    try {
      Optional<Recipient> recipient = recipients.findByIdAndUserId(recipientId, userId);
      if (recipient.isEmpty()) {
        return RecipientReputationEvidence.unavailable();
      }
      Optional<IdentityMatch> match = identity.findForRecipient(recipient.get());
      if (match.isEmpty()) {
        return RecipientReputationEvidence.noData();
      }
      IdentityMatch resolved = match.get();
      GlobalRecipientEntity entity = resolved.entity();
      ReputationStatus status = entity.getReputationStatus();
      int points =
          RecipientReputationPoints.points(status, resolved.matchLevel(), resolved.confidence());
      return new RecipientReputationEvidence(
          MatchStatus.MATCH_FOUND,
          status,
          entity.getReputationCategory(),
          entity.getReputationScore(),
          points,
          resolved.matchLevel(),
          resolved.confidence(),
          entity.getEvidenceCount(),
          entity.getReportCount(),
          "GLOBAL_RECIPIENT_" + status.name() + "_" + resolved.matchLevel().name());
    } catch (RuntimeException failure) {
      log.warn("Recipient intelligence lookup failed; continuing without it", failure);
      return RecipientReputationEvidence.unavailable();
    }
  }
}
