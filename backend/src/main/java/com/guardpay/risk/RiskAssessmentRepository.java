package com.guardpay.risk;

import com.guardpay.transaction.Transaction;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Basic persistence for {@link RiskAssessment} plus per-transaction history for the capture gate. */
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

  List<RiskAssessment> findByTransactionOrderByCreatedAtDesc(Transaction transaction);

  /** Batch lookup for a history page (avoids a query per row). */
  List<RiskAssessment> findByTransactionIdInOrderByCreatedAtDesc(Collection<UUID> transactionIds);

  @Query("select count(a) from RiskAssessment a where a.transaction.user.id = :userId")
  long countForUser(@Param("userId") UUID userId);

  @Query(
      """
      select count(a) from RiskAssessment a
      where a.transaction.user.id = :userId
        and a.recommendedAction = :action
      """)
  long countForUserByAction(
      @Param("userId") UUID userId, @Param("action") RecommendedAction action);

  @Query(
      """
      select coalesce(sum(a.transaction.amount), 0) from RiskAssessment a
      where a.transaction.user.id = :userId
        and a.recommendedAction = :action
      """)
  BigDecimal sumAmountForUserByAction(
      @Param("userId") UUID userId, @Param("action") RecommendedAction action);

  @Query("select avg(a.score) from RiskAssessment a where a.transaction.user.id = :userId")
  Double averageScoreForUser(@Param("userId") UUID userId);
}
