package com.guardpay.recipient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Basic persistence for {@link Recipient}. The owner-scoped lookup avoids
 * traversing {@code User.recipients}, which is lazy and unavailable outside a
 * transaction (open-in-view is disabled).
 */
public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

  /** Owner-scoped lookup for explicit recipient IDs; never expose another user's row. */
  Optional<Recipient> findByIdAndUserId(UUID id, UUID userId);

  /** All recipients owned by one user, used for intelligence authorization. */
  List<Recipient> findByUserId(UUID userId);

  Optional<Recipient> findByUserIdAndNameIgnoreCase(UUID userId, String name);

  Optional<Recipient> findByUserIdAndEmailIgnoreCase(UUID userId, String email);

  Optional<Recipient> findByUserIdAndPaypalAccountId(UUID userId, String paypalAccountId);
}
