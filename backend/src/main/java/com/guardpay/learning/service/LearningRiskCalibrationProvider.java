package com.guardpay.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.learning.domain.LearningModel;
import com.guardpay.learning.domain.ModelStatus;
import com.guardpay.learning.repository.LearningModelRepository;
import com.guardpay.risk.RiskCalibration;
import com.guardpay.risk.RiskCalibrationProvider;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Supplies the ACTIVE rule-calibration model, if one is deployed.
 *
 * <p>Fail-safe by construction: missing, inactive, malformed, or out-of-range
 * artifacts all resolve to the deterministic defaults. No learning failure can
 * change a payment decision.
 */
@Component
@Primary
public class LearningRiskCalibrationProvider implements RiskCalibrationProvider {

  public static final String MODEL_TYPE = "RULE_CALIBRATION";

  private static final Logger log =
      LoggerFactory.getLogger(LearningRiskCalibrationProvider.class);

  private final LearningModelRepository models;
  private final ObjectMapper objectMapper;

  public LearningRiskCalibrationProvider(
      LearningModelRepository models, ObjectMapper objectMapper) {
    this.models = models;
    this.objectMapper = objectMapper;
  }

  @Override
  public RiskCalibration activeCalibration() {
    try {
      Optional<LearningModel> active =
          models.findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
              ModelStatus.ACTIVE, MODEL_TYPE);
      if (active.isEmpty()) {
        return RiskCalibration.defaults();
      }
      LearningModel model = active.get();
      JsonNode artifact = objectMapper.readTree(model.getArtifactJson());
      int verify = requireInt(artifact, "verifyThreshold");
      int stop = requireInt(artifact, "stopThreshold");
      if (verify <= 0 || stop <= verify || stop > 100) {
        log.warn(
            "Ignoring invalid deployed calibration {} (verify={}, stop={})",
            model.getModelVersion(),
            verify,
            stop);
        return RiskCalibration.defaults();
      }
      return RiskCalibration.ofThresholds(
          verify, stop, model.getModelVersion(), model.getDatasetChecksum());
    } catch (Exception failure) {
      log.warn("Deployed calibration unavailable, using deterministic defaults", failure);
      return RiskCalibration.defaults();
    }
  }

  private static int requireInt(JsonNode artifact, String field) {
    JsonNode node = artifact.get(field);
    if (node == null || !node.isIntegralNumber()) {
      throw new IllegalArgumentException("artifact missing integer " + field);
    }
    return node.asInt();
  }
}
