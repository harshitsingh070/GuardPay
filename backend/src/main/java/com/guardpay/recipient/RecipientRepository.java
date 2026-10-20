package com.guardpay.recipient;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Basic persistence for {@link Recipient}. The owner-scoped lookup avoids
 * traversing {@code User.recipients}, which is lazy and unavailable outside a
 * transaction (open-in-view is disabled).
 */
public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

  Optional<Recipient> findByUserIdAndNameIgnoreCase(UUID userId, String name);
}
