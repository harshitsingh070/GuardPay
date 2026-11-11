package com.guardpay.feedback;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFeedbackEventRepository extends JpaRepository<FraudFeedbackEvent, UUID> {

  List<FraudFeedbackEvent> findByFeedbackIdOrderByOccurredAtDesc(UUID feedbackId);
}
