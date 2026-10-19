package com.guardpay.paypal.webhook;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lookup by PayPal event ID is the idempotency check — no complex queries. */
public interface PayPalWebhookEventRepository extends JpaRepository<PayPalWebhookEvent, UUID> {

  Optional<PayPalWebhookEvent> findByPaypalEventId(String paypalEventId);
}
