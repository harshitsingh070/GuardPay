package com.guardpay.recipient;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Basic persistence for {@link Recipient}. No custom queries in this milestone. */
public interface RecipientRepository extends JpaRepository<Recipient, UUID> {}
