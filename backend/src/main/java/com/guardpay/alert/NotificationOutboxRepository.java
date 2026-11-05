package com.guardpay.alert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Delivery queue for alerts. */
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

  boolean existsByAlertIdAndChannel(UUID alertId, NotificationChannel channel);

  List<NotificationOutbox> findTop50ByStatusOrderByCreatedAtAsc(NotificationStatus status);

  List<NotificationOutbox> findTop200ByStatusOrderByCreatedAtDesc(NotificationStatus status);

  long countByStatus(NotificationStatus status);

  long countByAlertUserIdAndStatus(UUID userId, NotificationStatus status);
}
