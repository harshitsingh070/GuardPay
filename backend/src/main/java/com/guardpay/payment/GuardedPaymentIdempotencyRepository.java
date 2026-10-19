package com.guardpay.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Key lookup for guarded-payment replay. The UNIQUE constraint is the arbiter. */
public interface GuardedPaymentIdempotencyRepository
    extends JpaRepository<GuardedPaymentIdempotency, UUID> {

  Optional<GuardedPaymentIdempotency> findByIdempotencyKey(String idempotencyKey);
}
