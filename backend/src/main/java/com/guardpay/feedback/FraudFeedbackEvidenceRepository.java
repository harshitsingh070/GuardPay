package com.guardpay.feedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFeedbackEvidenceRepository
    extends JpaRepository<FraudFeedbackEvidence, UUID> {

  Optional<FraudFeedbackEvidence> findByFeedbackIdAndEvidenceFingerprint(
      UUID feedbackId, String evidenceFingerprint);

  List<FraudFeedbackEvidence> findByFeedbackIdOrderByObservedAtDesc(UUID feedbackId);
}
