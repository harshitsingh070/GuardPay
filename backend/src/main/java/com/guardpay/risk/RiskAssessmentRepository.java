package com.guardpay.risk;

import com.guardpay.transaction.Transaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Basic persistence for {@link RiskAssessment} plus per-transaction history for the capture gate. */
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

  List<RiskAssessment> findByTransactionOrderByCreatedAtDesc(Transaction transaction);
}
