package com.guardpay.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Basic persistence for {@link PaymentContext}. No custom queries in this milestone. */
public interface PaymentContextRepository extends JpaRepository<PaymentContext, UUID> {}
