package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.RecipientReviewEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only admin review trail. */
public interface RecipientReviewEventRepository
    extends JpaRepository<RecipientReviewEvent, UUID> {

  List<RecipientReviewEvent> findByRecipientEntityIdOrderByCreatedAtDesc(
      UUID recipientEntityId);
}
