package com.guardpay.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.learning.domain.LearningModel;
import com.guardpay.learning.domain.ModelStatus;
import com.guardpay.learning.repository.LearningModelRepository;
import com.guardpay.risk.RiskCalibration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningRiskCalibrationProviderTest {

  @Mock private LearningModelRepository models;

  private LearningRiskCalibrationProvider provider;

  @BeforeEach
  void setUp() {
    provider = new LearningRiskCalibrationProvider(models, new ObjectMapper());
  }

  @Test
  void returnsDefaultsWhenNothingIsDeployed() {
    when(models.findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
            ModelStatus.ACTIVE, LearningRiskCalibrationProvider.MODEL_TYPE))
        .thenReturn(Optional.empty());

    RiskCalibration calibration = provider.activeCalibration();

    assertTrue(calibration.isDefault());
    assertEquals(40, calibration.verifyThreshold());
    assertEquals(85, calibration.stopThreshold());
  }

  @Test
  void readsAValidDeployedArtifact() {
    LearningModel model = new LearningModel();
    model.setModelVersion("calib-v1-abc");
    model.setDatasetChecksum("checksum");
    model.setArtifactJson("{\"verifyThreshold\":30,\"stopThreshold\":78}");
    when(models.findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
            ModelStatus.ACTIVE, LearningRiskCalibrationProvider.MODEL_TYPE))
        .thenReturn(Optional.of(model));

    RiskCalibration calibration = provider.activeCalibration();

    assertFalse(calibration.isDefault());
    assertEquals(30, calibration.verifyThreshold());
    assertEquals(78, calibration.stopThreshold());
    assertEquals("calib-v1-abc", calibration.modelVersion());
  }

  @Test
  void malformedArtifactFallsBackToDefaults() {
    LearningModel model = new LearningModel();
    model.setModelVersion("broken");
    model.setArtifactJson("{not-json");
    when(models.findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
            ModelStatus.ACTIVE, LearningRiskCalibrationProvider.MODEL_TYPE))
        .thenReturn(Optional.of(model));

    assertTrue(provider.activeCalibration().isDefault());
  }

  @Test
  void outOfRangeArtifactFallsBackToDefaults() {
    LearningModel model = new LearningModel();
    model.setModelVersion("bad-range");
    model.setArtifactJson("{\"verifyThreshold\":90,\"stopThreshold\":20}");
    when(models.findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
            ModelStatus.ACTIVE, LearningRiskCalibrationProvider.MODEL_TYPE))
        .thenReturn(Optional.of(model));

    assertTrue(provider.activeCalibration().isDefault());
  }
}
