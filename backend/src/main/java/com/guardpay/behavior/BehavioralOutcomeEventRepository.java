package com.guardpay.behavior;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for the behavioral outcome idempotency/exclusion ledger. */
public interface BehavioralOutcomeEventRepository
    extends JpaRepository<BehavioralOutcomeEvent, UUID> {

  Optional<BehavioralOutcomeEvent> findByTransactionIdAndOutcome(
      UUID transactionId, BehavioralOutcome outcome);

  boolean existsByTransactionIdAndOutcome(UUID transactionId, BehavioralOutcome outcome);

  List<BehavioralOutcomeEvent> findByTransactionId(UUID transactionId);

  /**
   * Transaction ids that must never contribute to the learned baseline.
   * Includes failed/cancelled attempts, stopped/suspicious completions, and
   * payments later refunded, reversed, or disputed.
   */
  @Query(
      """
      select e.transaction.id from BehavioralOutcomeEvent e
      where e.transaction.user.id = :userId
        and e.learnable = false
      """)
  Set<UUID> findNonLearnableTransactionIds(@Param("userId") UUID userId);

  @Query(
      """
      select e.transaction.id from BehavioralOutcomeEvent e
      where e.transaction.user.id = :userId
        and e.outcome in :outcomes
      """)
  Set<UUID> findTransactionIdsByOutcomeIn(
      @Param("userId") UUID userId,
      @Param("outcomes") Collection<BehavioralOutcome> outcomes);
}
