package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.RecipientFraudEvidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Deduplicated evidence persistence. */
public interface RecipientFraudEvidenceRepository
    extends JpaRepository<RecipientFraudEvidence, UUID> {

  Optional<RecipientFraudEvidence> findByRecipientEntityIdAndEvidenceFingerprint(
      UUID recipientEntityId, String evidenceFingerprint);

  List<RecipientFraudEvidence> findByRecipientEntityIdOrderByObservedAtDesc(
      UUID recipientEntityId);

  List<RecipientFraudEvidence> findByRecipientEntityIdAndRevokedAtIsNull(UUID recipientEntityId);

  long countByRecipientEntityIdAndRevokedAtIsNull(UUID recipientEntityId);

  @Query(
      """
      select count(distinct e.reporterGroupKey) from RecipientFraudEvidence e
      where e.recipientEntity.id = :entityId
        and e.reporterGroupKey is not null
        and e.revokedAt is null
      """)
  long countDistinctReporters(@Param("entityId") UUID entityId);
}
