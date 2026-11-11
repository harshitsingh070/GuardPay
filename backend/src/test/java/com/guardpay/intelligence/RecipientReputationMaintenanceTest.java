package com.guardpay.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guardpay.intelligence.config.RecipientIntelligenceProperties;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.RecipientFraudEvidence;
import com.guardpay.intelligence.domain.ReputationCategory;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.repository.RecipientFraudEvidenceRepository;
import com.guardpay.intelligence.repository.RecipientReputationSnapshotRepository;
import com.guardpay.intelligence.service.RecipientReputationCalculator;
import com.guardpay.intelligence.service.RecipientReputationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class RecipientReputationMaintenanceTest {

  private final GlobalRecipientEntityRepository entities =
      mock(GlobalRecipientEntityRepository.class);
  private final RecipientFraudEvidenceRepository evidence =
      mock(RecipientFraudEvidenceRepository.class);
  private final RecipientReputationSnapshotRepository snapshots =
      mock(RecipientReputationSnapshotRepository.class);
  private final RecipientReputationCalculator calculator = mock(RecipientReputationCalculator.class);

  @Test
  void staleEntitiesPastRetentionAreExpired() {
    RecipientIntelligenceProperties properties = new RecipientIntelligenceProperties();
    properties.setStaleCalculationHours(24);
    properties.setEvidenceRetentionDays(365);
    RecipientReputationService service = service(properties);

    GlobalRecipientEntity entity = new GlobalRecipientEntity();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    entity.setReputationStatus(ReputationStatus.REPORTED);
    entity.setReputationScore(25);
    entity.setLastCalculatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
    entity.setLastEvidenceAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(400));

    when(entities.findStaleForRecalculation(anyList(), any(), any(Pageable.class)))
        .thenReturn(List.of(entity));
    when(evidence.findByRecipientEntityIdOrderByObservedAtDesc(entity.getId()))
        .thenReturn(List.<RecipientFraudEvidence>of());
    when(calculator.calculate(anyList(), eq(ReputationStatus.REPORTED), any(), anyInt()))
        .thenReturn(
            new RecipientReputationCalculator.Calculation(
                25, ReputationCategory.ELEVATED_RISK, 1, 1, 1, null, "v1"));

    int refreshed = service.refreshStaleEntities();

    assertEquals(1, refreshed);
    assertEquals(ReputationStatus.EXPIRED, entity.getReputationStatus());
    assertEquals(0, entity.getReputationScore());
    assertEquals(ReputationCategory.NO_DATA, entity.getReputationCategory());
    verify(entities, org.mockito.Mockito.atLeast(2)).save(entity);
    verify(snapshots).save(any());
  }

  @Test
  void staleEntitiesWithRecentEvidenceKeepTheirStatus() {
    RecipientIntelligenceProperties properties = new RecipientIntelligenceProperties();
    properties.setStaleCalculationHours(24);
    properties.setEvidenceRetentionDays(365);
    RecipientReputationService service = service(properties);

    GlobalRecipientEntity entity = new GlobalRecipientEntity();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    entity.setReputationStatus(ReputationStatus.REPORTED);
    entity.setLastEvidenceAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10));

    when(entities.findStaleForRecalculation(anyList(), any(), any(Pageable.class)))
        .thenReturn(List.of(entity));
    when(evidence.findByRecipientEntityIdOrderByObservedAtDesc(entity.getId()))
        .thenReturn(List.<RecipientFraudEvidence>of());
    when(calculator.calculate(anyList(), eq(ReputationStatus.REPORTED), any(), anyInt()))
        .thenReturn(
            new RecipientReputationCalculator.Calculation(
                25,
                ReputationCategory.ELEVATED_RISK,
                1,
                1,
                1,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(10),
                "v1"));

    service.refreshStaleEntities();

    assertEquals(ReputationStatus.REPORTED, entity.getReputationStatus());
  }

  @Test
  void disabledIntelligenceSkipsMaintenanceEntirely() {
    RecipientIntelligenceProperties properties = new RecipientIntelligenceProperties();
    properties.setEnabled(false);
    RecipientReputationService service = service(properties);

    assertEquals(0, service.refreshStaleEntities());
    verifyNoInteractions(entities, evidence, snapshots, calculator);
  }

  private RecipientReputationService service(RecipientIntelligenceProperties properties) {
    return new RecipientReputationService(entities, evidence, snapshots, calculator, properties);
  }
}
