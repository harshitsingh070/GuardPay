package com.guardpay.behavior;

import com.guardpay.recipient.Recipient;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-recipient behavioral statistics for one user. */
public interface UserRecipientBehaviorRepository
    extends JpaRepository<UserRecipientBehavior, UUID> {

  Optional<UserRecipientBehavior> findByUserIdAndRecipientId(
      UUID userId, UUID recipientId);

  void deleteByUserId(UUID userId);

  @Query(
      """
      select r from UserRecipientBehavior r
      where r.user.id = :userId
      order by r.transactionCount desc
      """)
  List<UserRecipientBehavior> findByUserIdOrderByTransactionCountDesc(
      @Param("userId") UUID userId, Pageable pageable);

  @Query(
      """
      select count(r) from UserRecipientBehavior r
      where r.user.id = :userId
        and r.transactionCount >= :establishedThreshold
      """)
  long countEstablishedRecipients(
      @Param("userId") UUID userId,
      @Param("establishedThreshold") int establishedThreshold);
}
