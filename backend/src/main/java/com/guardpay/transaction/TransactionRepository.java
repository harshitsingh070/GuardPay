package com.guardpay.transaction;

import com.guardpay.merchant.Merchant;
import com.guardpay.recipient.Recipient;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  /**
   * Row-locked lookup for verification confirmation: serializes concurrent
   * confirmations for the same payment so only one order is ever created.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Transaction t where t.id = :id")
  Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

  List<Transaction> findByUserAndStatus(User user, TransactionStatus status);

  /** Paged transaction history for one user (owner-scoped access). */
  Page<Transaction> findByUserId(UUID userId, Pageable pageable);

  /** Attempts (any status) created after a cutoff, used for velocity analysis. */
  List<Transaction> findByUserAndTransactionTimestampAfter(
      User user, OffsetDateTime transactionTimestamp);

  List<Transaction> findByUserAndRecipientAndStatus(
      User user, Recipient recipient, TransactionStatus status);

  List<Transaction> findByUserAndMerchantAndStatus(
      User user, Merchant merchant, TransactionStatus status);

  /** Bounded successful history for one recipient, scoped to the owning user. */
  @Query(
      """
      select t from Transaction t
      where t.user.id = :userId
        and t.recipient.id = :recipientId
        and t.status = com.guardpay.transaction.TransactionStatus.COMPLETED
        and (:currency is null or upper(t.currency) = upper(:currency))
        and t.transactionTimestamp < :asOf
        and t.id <> :excludeTransactionId
      order by t.transactionTimestamp desc
      """)
  List<Transaction> findRecipientCompletedHistory(
      @Param("userId") UUID userId,
      @Param("recipientId") UUID recipientId,
      @Param("currency") String currency,
      @Param("asOf") OffsetDateTime asOf,
      @Param("excludeTransactionId") UUID excludeTransactionId,
      Pageable pageable);

  /** Counts failed/cancelled attempts for one recipient without future rows. */
  @Query(
      """
      select count(t) from Transaction t
      where t.user.id = :userId
        and t.recipient.id = :recipientId
        and t.status in :statuses
        and t.transactionTimestamp < :asOf
        and t.id <> :excludeTransactionId
      """)
  long countRecipientUnsuccessfulAttempts(
      @Param("userId") UUID userId,
      @Param("recipientId") UUID recipientId,
      @Param("statuses") Collection<TransactionStatus> statuses,
      @Param("asOf") OffsetDateTime asOf,
      @Param("excludeTransactionId") UUID excludeTransactionId);

  /** Bounded recent attempts (any status) for one recipient, scoped to the owner. */
  List<Transaction> findByUserIdAndRecipientIdAndTransactionTimestampAfter(
      UUID userId, UUID recipientId, OffsetDateTime transactionTimestamp, Pageable pageable);
}
