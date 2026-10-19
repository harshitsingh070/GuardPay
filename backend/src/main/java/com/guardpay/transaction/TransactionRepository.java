package com.guardpay.transaction;

import com.guardpay.merchant.Merchant;
import com.guardpay.recipient.Recipient;
import com.guardpay.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Basic persistence for {@link Transaction} plus lookups for the capture gate and history analysis. */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  Optional<Transaction> findByPaypalOrderId(String paypalOrderId);

  Optional<Transaction> findByPaypalCaptureId(String paypalCaptureId);

  /**
   * Row-locked lookup for the capture gate: serializes simultaneous capture
   * attempts so only one PayPal capture can happen per order.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Transaction t where t.paypalOrderId = :orderId")
  Optional<Transaction> findByPaypalOrderIdForUpdate(@Param("orderId") String orderId);

  List<Transaction> findByUserAndStatus(User user, TransactionStatus status);

  List<Transaction> findByUserAndRecipientAndStatus(
      User user, Recipient recipient, TransactionStatus status);

  List<Transaction> findByUserAndMerchantAndStatus(
      User user, Merchant merchant, TransactionStatus status);
}
