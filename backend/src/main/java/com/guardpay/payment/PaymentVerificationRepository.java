package com.guardpay.payment;

import com.guardpay.transaction.Transaction;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for controlled VERIFY challenges (one per payment attempt). */
public interface PaymentVerificationRepository extends JpaRepository<PaymentVerification, UUID> {

  Optional<PaymentVerification> findByTransaction(Transaction transaction);

  Optional<PaymentVerification> findByTransactionId(UUID transactionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from PaymentVerification v where v.transaction.id = :transactionId")
  Optional<PaymentVerification> findByTransactionIdForUpdate(
      @Param("transactionId") UUID transactionId);
}
