package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.RecipientIntelligenceAccessLog;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Explicit intelligence access audit. */
public interface RecipientIntelligenceAccessLogRepository
    extends JpaRepository<RecipientIntelligenceAccessLog, UUID> {

  long countByActorReferenceAndAccessedAtAfter(UUID actorReference, OffsetDateTime after);
}
