package com.guardpay.feedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for explicit fraud/legitimate feedback labels. */
public interface FraudFeedbackRepository extends JpaRepository<FraudFeedback, UUID> {

  Optional<FraudFeedback> findByTransactionId(UUID transactionId);

  Optional<FraudFeedback> findByTransactionIdAndUserReference(
      UUID transactionId, UUID userReference);

  List<FraudFeedback> findAllByOrderByCreatedAtDesc();
}
