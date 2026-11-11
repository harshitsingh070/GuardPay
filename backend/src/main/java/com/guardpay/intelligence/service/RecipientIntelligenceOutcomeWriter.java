package com.guardpay.intelligence.service;

import com.guardpay.intelligence.config.RecipientIntelligenceProperties;
import com.guardpay.intelligence.domain.EvidenceSource;
import com.guardpay.intelligence.domain.EvidenceType;
import com.guardpay.intelligence.domain.EvidenceVerificationStatus;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.service.RecipientIdentityService.IdentityMatch;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes provider-outcome evidence in its own transaction.
 *
 * <p>Disputes and refunds are not proof of fraud: they create low-weight,
 * unverified evidence and can never confirm an entity. Failures are isolated
 * from the payment/webhook transaction.
 */
@Service
public class RecipientIntelligenceOutcomeWriter {

  private final RecipientIdentityService identity;
  private final RecipientReputationService reputation;
  private final RecipientRepository recipients;
  private final RecipientIntelligenceProperties properties;

  public RecipientIntelligenceOutcomeWriter(
      RecipientIdentityService identity,
      RecipientReputationService reputation,
      RecipientRepository recipients,
      RecipientIntelligenceProperties properties) {
    this.identity = identity;
    this.reputation = reputation;
    this.recipients = recipients;
    this.properties = properties;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID userId, UUID recipientId, UUID transactionId, String source) {
    if (!properties.isEnabled()) {
      return;
    }
    if (userId == null || recipientId == null || transactionId == null) {
      return;
    }
    if (!identity.isConfigured()) {
      return;
    }
    Optional<Recipient> recipient = recipients.findByIdAndUserId(recipientId, userId);
    if (recipient.isEmpty()) {
      return;
    }
    Optional<IdentityMatch> match = identity.resolveForRecipient(recipient.get());
    if (match.isEmpty()) {
      return;
    }
    GlobalRecipientEntity entity = match.get().entity();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String fingerprint =
        RecipientIdentifierHasher.sha256Hex(
            entity.getId() + "|PROVIDER_DISPUTE_OUTCOME|" + transactionId);
    reputation.recordEvidence(
        entity,
        EvidenceType.DISPUTE_OPENED,
        EvidenceSource.PROVIDER_DISPUTE_OUTCOME,
        0.6,
        EvidenceVerificationStatus.UNVERIFIED,
        fingerprint,
        null,
        transactionId,
        now,
        null);
    reputation.recalculate(entity);
  }
}
