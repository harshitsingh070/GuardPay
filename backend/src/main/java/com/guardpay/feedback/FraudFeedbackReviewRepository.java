package com.guardpay.feedback;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFeedbackReviewRepository extends JpaRepository<FraudFeedbackReview, UUID> {

  List<FraudFeedbackReview> findByFeedbackIdOrderByReviewedAtDesc(UUID feedbackId);
}
