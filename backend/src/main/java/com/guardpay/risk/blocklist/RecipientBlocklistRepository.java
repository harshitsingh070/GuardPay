package com.guardpay.risk.blocklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the counterparty blocklist (owner-scoped). */
public interface RecipientBlocklistRepository
    extends JpaRepository<RecipientBlocklistEntry, UUID> {

  boolean existsByUserIdAndRecipientId(UUID userId, UUID recipientId);

  Optional<RecipientBlocklistEntry> findByUserIdAndRecipientId(
      UUID userId, UUID recipientId);

  List<RecipientBlocklistEntry> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
