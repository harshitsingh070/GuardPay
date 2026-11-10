package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.IdentifierType;
import com.guardpay.intelligence.domain.RecipientIdentifier;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Hashed identifier persistence; raw values are never stored. */
public interface RecipientIdentifierRepository extends JpaRepository<RecipientIdentifier, UUID> {

  Optional<RecipientIdentifier> findByIdentifierTypeAndIdentifierHmac(
      IdentifierType identifierType, String identifierHmac);

  List<RecipientIdentifier> findAllByIdentifierTypeAndIdentifierHmacIn(
      IdentifierType identifierType, Collection<String> identifierHmacs);

  List<RecipientIdentifier> findByRecipientEntityId(UUID recipientEntityId);
}
