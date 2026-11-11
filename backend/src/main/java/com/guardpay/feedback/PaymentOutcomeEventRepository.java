package com.guardpay.feedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOutcomeEventRepository extends JpaRepository<PaymentOutcomeEvent, UUID> {

  Optional<PaymentOutcomeEvent> findByEventFingerprint(String eventFingerprint);

  List<PaymentOutcomeEvent> findByTransactionIdOrderByOccurredAtDesc(UUID transactionId);
}
