package com.guardpay.alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Owner-scoped alert feed and aggregate counts for the dashboard. */
public interface AlertRepository extends JpaRepository<Alert, UUID> {

  boolean existsByTransactionIdAndType(UUID transactionId, AlertType type);

  List<Alert> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserId(UUID userId);

  long countByUserIdAndSeverity(UUID userId, AlertSeverity severity);

  @Query("select a from Alert a join fetch a.user where a.id = :id")
  Optional<Alert> findWithUserById(@Param("id") UUID id);
}
