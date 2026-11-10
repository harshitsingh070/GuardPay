package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.RecipientRelationship;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Per-user relationships to global recipient entities. */
public interface RecipientRelationshipRepository
    extends JpaRepository<RecipientRelationship, UUID> {

  Optional<RecipientRelationship> findByOwnerReferenceAndRecipientEntityId(
      UUID ownerReference, UUID recipientEntityId);

  List<RecipientRelationship> findByOwnerReference(UUID ownerReference);
}
