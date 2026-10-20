package com.guardpay.payment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Serializes work for one idempotency key across application instances.
 *
 * <p>Uses PostgreSQL's transaction-scoped advisory lock, so the lock is held
 * from reservation through the PayPal call and released automatically on
 * commit/rollback. Must be called inside the guarded-payment transaction.
 */
@Component
public class GuardedPaymentLock {

  @PersistenceContext private EntityManager entityManager;

  public void acquire(String idempotencyKey) {
    entityManager
        .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
        .setParameter("key", idempotencyKey)
        .getResultList();
  }
}
