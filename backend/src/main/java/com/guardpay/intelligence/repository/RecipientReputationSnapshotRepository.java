package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.RecipientReputationSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Calculation history for global recipient reputation. */
public interface RecipientReputationSnapshotRepository
    extends JpaRepository<RecipientReputationSnapshot, UUID> {

  Optional<RecipientReputationSnapshot> findTopByRecipientEntityIdOrderByCalculatedAtDesc(
      UUID recipientEntityId);

  List<RecipientReputationSnapshot> findByRecipientEntityIdOrderByCalculatedAtDesc(
      UUID recipientEntityId);
}
