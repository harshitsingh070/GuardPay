package com.guardpay.payment;

import com.guardpay.transaction.Transaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for controlled VERIFY challenges (one per payment attempt). */
public interface PaymentVerificationRepository extends JpaRepository<PaymentVerification, UUID> {

  Optional<PaymentVerification> findByTransaction(Transaction transaction);

  Optional<PaymentVerification> findByTransactionId(UUID transactionId);
}
