package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.ReputationCategory;
import com.guardpay.intelligence.domain.ReputationStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for global recipient entities. */
public interface GlobalRecipientEntityRepository
    extends JpaRepository<GlobalRecipientEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from GlobalRecipientEntity e where e.id = :id")
  Optional<GlobalRecipientEntity> findByIdForUpdate(@Param("id") UUID id);

  long countByReputationStatus(ReputationStatus status);

  long countByReputationCategory(ReputationCategory category);

  long countByReputationStatusIn(Collection<ReputationStatus> statuses);

  /**
   * Entities whose stored score is older than the cutoff. Used by the scheduled
   * maintenance job so evidence decay and expiry are actually applied.
   */
  @Query(
      """
      select e from GlobalRecipientEntity e
      where e.reputationStatus in :statuses
        and (e.lastCalculatedAt is null or e.lastCalculatedAt < :cutoff)
      order by e.lastCalculatedAt asc
      """)
  List<GlobalRecipientEntity> findStaleForRecalculation(
      @Param("statuses") Collection<ReputationStatus> statuses,
      @Param("cutoff") OffsetDateTime cutoff,
      Pageable pageable);
}
