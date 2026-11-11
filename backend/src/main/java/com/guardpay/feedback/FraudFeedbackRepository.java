package com.guardpay.feedback;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for explicit fraud/legitimate feedback labels. */
public interface FraudFeedbackRepository extends JpaRepository<FraudFeedback, UUID> {

  Optional<FraudFeedback> findByTransactionId(UUID transactionId);

  Optional<FraudFeedback> findByTransactionIdAndUserReference(
      UUID transactionId, UUID userReference);

  List<FraudFeedback> findAllByOrderByCreatedAtDesc();

  Page<FraudFeedback> findByUserReferenceOrderByCreatedAtDesc(
      UUID userReference, Pageable pageable);

  Page<FraudFeedback> findByFeedbackStatusOrderByCreatedAtDesc(
      FeedbackStatus status, Pageable pageable);

  List<FraudFeedback> findByFeedbackStatusInOrderByCreatedAtAsc(
      Collection<FeedbackStatus> statuses);

  long countByFeedbackStatus(FeedbackStatus status);

  long countByUserReference(UUID userReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FraudFeedback f where f.id = :id")
  Optional<FraudFeedback> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select count(f) from FraudFeedback f
      where f.feedbackStatus in :statuses
      """)
  long countByFeedbackStatusIn(@Param("statuses") Collection<FeedbackStatus> statuses);
}
