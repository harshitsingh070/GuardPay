package com.guardpay.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.IdentifierVerificationStatus;
import com.guardpay.intelligence.domain.RecipientIdentifier;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.domain.ReviewAction;
import com.guardpay.intelligence.repository.FraudIntelligenceReportRepository;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.repository.RecipientFraudEvidenceRepository;
import com.guardpay.intelligence.repository.RecipientIdentifierRepository;
import com.guardpay.intelligence.repository.RecipientReviewEventRepository;
import com.guardpay.intelligence.service.RecipientReputationService;
import com.guardpay.intelligence.service.RecipientReviewService;
import com.guardpay.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipientReviewServiceTest {

  @Mock private GlobalRecipientEntityRepository entities;
  @Mock private FraudIntelligenceReportRepository reports;
  @Mock private RecipientFraudEvidenceRepository evidence;
  @Mock private RecipientReviewEventRepository reviewEvents;
  @Mock private RecipientIdentifierRepository identifiers;
  @Mock private RecipientReputationService reputation;

  @Test
  void confirmingFraudVerifiesIdentifiers() {
    RecipientReviewService service =
        new RecipientReviewService(
            entities, reports, evidence, reviewEvents, identifiers, reputation);
    GlobalRecipientEntity entity = new GlobalRecipientEntity();
    UUID entityId = UUID.randomUUID();
    ReflectionTestUtils.setField(entity, "id", entityId);
    entity.setReputationStatus(ReputationStatus.UNDER_REVIEW);
    RecipientIdentifier identifier = new RecipientIdentifier();
    identifier.setVerificationStatus(IdentifierVerificationStatus.UNVERIFIED);

    when(entities.findByIdForUpdate(entityId)).thenReturn(Optional.of(entity));
    when(reports.findByRecipientEntityIdAndReportStatusIn(any(), any()))
        .thenReturn(List.of());
    when(identifiers.findByRecipientEntityId(entityId)).thenReturn(List.of(identifier));
    User admin = mock(User.class);
    when(admin.getId()).thenReturn(UUID.randomUUID());

    service.review(entityId, admin, ReviewAction.CONFIRM_FRAUD, "confirmed");

    assertEquals(IdentifierVerificationStatus.VERIFIED, identifier.getVerificationStatus());
    verify(identifiers).save(identifier);
  }
}
