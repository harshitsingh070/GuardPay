package com.guardpay.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.feedback.FeedbackLabel;
import com.guardpay.learning.domain.DatasetStatus;
import com.guardpay.learning.domain.LearningDataset;
import com.guardpay.learning.domain.LearningDatasetItem;
import com.guardpay.learning.domain.LearningEventType;
import com.guardpay.learning.domain.LearningModel;
import com.guardpay.learning.domain.ModelStatus;
import com.guardpay.learning.repository.LearningDatasetItemRepository;
import com.guardpay.learning.repository.LearningDatasetRepository;
import com.guardpay.learning.repository.LearningEventRepository;
import com.guardpay.learning.repository.LearningModelRepository;
import com.guardpay.learning.web.dto.LearningDtos.ActiveCalibrationDto;
import com.guardpay.learning.web.dto.LearningDtos.EventDto;
import com.guardpay.learning.web.dto.LearningDtos.ModelDto;
import com.guardpay.learning.web.dto.LearningDtos.OverviewDto;
import com.guardpay.learning.web.dto.LearningDtos.TrainingResultDto;
import com.guardpay.risk.RiskCalibration;
import com.guardpay.risk.RiskCalibrationProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Trains, evaluates, approves, deploys, and rolls back rule-calibration models.
 *
 * <p>Training never fabricates data and never auto-deploys. A candidate can
 * only affect decisions after an explicit admin approval AND deployment.
 */
@Service
public class LearningModelService {

  private static final Logger log = LoggerFactory.getLogger(LearningModelService.class);
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static final int DEFAULT_STOP = 85;
  private static final int DEFAULT_VERIFY = 40;
  private static final int FALSE_NEGATIVE_COST = 5;
  private static final int FALSE_POSITIVE_COST = 1;

  private final LearningModelRepository models;
  private final LearningDatasetRepository datasets;
  private final LearningDatasetItemRepository items;
  private final LearningEventRepository events;
  private final LearningDatasetService datasetService;
  private final LearningAuditService audit;
  private final RiskCalibrationProvider calibrationProvider;

  public LearningModelService(
      LearningModelRepository models,
      LearningDatasetRepository datasets,
      LearningDatasetItemRepository items,
      LearningEventRepository events,
      LearningDatasetService datasetService,
      LearningAuditService audit,
      RiskCalibrationProvider calibrationProvider) {
    this.models = models;
    this.datasets = datasets;
    this.items = items;
    this.events = events;
    this.datasetService = datasetService;
    this.audit = audit;
    this.calibrationProvider = calibrationProvider;
  }

  @Transactional
  public TrainingResultDto train(UUID datasetId, UUID actor) {
    LearningDataset dataset = datasetService.require(datasetId);
    List<String> unmet =
        LearningDatasetService.unmetRequirements(
            dataset.getRecordCount(), dataset.getFraudCount(), dataset.getLegitimateCount());
    if (dataset.getStatus() != DatasetStatus.READY) {
      return failure(
          dataset,
          actor,
          "Dataset is "
              + dataset.getStatus()
              + (dataset.getInvalidationReason() == null
                  ? ""
                  : ": " + dataset.getInvalidationReason())
              + (unmet.isEmpty() ? "" : "; " + String.join("; ", unmet)));
    }
    if (!unmet.isEmpty()) {
      return failure(dataset, actor, String.join("; ", unmet));
    }

    List<LearningDatasetItem> all = items.findByDatasetIdOrderByOccurredAtAsc(dataset.getId());
    List<LearningDatasetItem> train = all.stream().filter(i -> "TRAIN".equals(i.getSplitName())).toList();
    List<LearningDatasetItem> validation =
        all.stream().filter(i -> "VALIDATION".equals(i.getSplitName())).toList();
    List<LearningDatasetItem> test =
        all.stream().filter(i -> "TEST".equals(i.getSplitName())).toList();
    if (!hasBothLabels(train)) {
      return failure(dataset, actor, "Training split must contain fraud and legitimate labels");
    }
    if (!hasBothLabels(validation)) {
      return failure(dataset, actor, "Validation split must contain fraud and legitimate labels");
    }
    if (!hasBothLabels(test)) {
      return failure(dataset, actor, "Test split must contain fraud and legitimate labels");
    }

    int stop = chooseStop(train);
    int verify = chooseVerify(train, stop);
    Metrics trainMetrics = metrics(train, verify);
    Metrics validationMetrics = metrics(validation, verify);
    Metrics testMetrics = metrics(test, verify);
    Metrics championTest = metrics(test, DEFAULT_VERIFY);

    String modelVersion = nextVersion(dataset);
    String artifact;
    String metricsJson;
    String comparison;
    try {
      artifact =
          JSON.writeValueAsString(
              Map.of(
                  "verifyThreshold", verify,
                  "stopThreshold", stop,
                  "positiveLabel", FeedbackLabel.FRAUD.name(),
                  "scoreField", "riskScore",
                  "featureVersion", dataset.getFeatureVersion(),
                  "labelPolicyVersion", dataset.getLabelPolicyVersion()));
      Map<String, Object> metricsMap = new LinkedHashMap<>();
      metricsMap.put("train", trainMetrics);
      metricsMap.put("validation", validationMetrics);
      metricsMap.put("test", testMetrics);
      metricsMap.put("verifyThreshold", verify);
      metricsMap.put("stopThreshold", stop);
      metricsMap.put("decisionCost", decisionCost(train, verify));
      metricsJson = JSON.writeValueAsString(metricsMap);
      Map<String, Object> comparisonMap = new LinkedHashMap<>();
      comparisonMap.put("candidateVerifyThreshold", verify);
      comparisonMap.put("candidateStopThreshold", stop);
      comparisonMap.put("championVerifyThreshold", DEFAULT_VERIFY);
      comparisonMap.put("championStopThreshold", DEFAULT_STOP);
      comparisonMap.put("candidateTest", testMetrics);
      comparisonMap.put("championTest", championTest);
      comparisonMap.put(
          "precisionDelta", round(testMetrics.precision() - championTest.precision()));
      comparisonMap.put("recallDelta", round(testMetrics.recall() - championTest.recall()));
      comparisonMap.put("f1Delta", round(testMetrics.f1() - championTest.f1()));
      comparison = JSON.writeValueAsString(comparisonMap);
    } catch (Exception failure) {
      return failure(dataset, actor, "Model artifact could not be serialized");
    }

    LearningModel model = new LearningModel();
    model.setModelVersion(modelVersion);
    model.setDatasetId(dataset.getId());
    model.setModelType(LearningRiskCalibrationProvider.MODEL_TYPE);
    model.setStatus(ModelStatus.CANDIDATE);
    model.setThresholdScore(stop);
    model.setPrecisionValue(testMetrics.precision());
    model.setRecallValue(testMetrics.recall());
    model.setF1Value(testMetrics.f1());
    model.setAccuracyValue(testMetrics.accuracy());
    model.setFalsePositiveRate(testMetrics.falsePositiveRate());
    model.setFalseNegativeRate(testMetrics.falseNegativeRate());
    model.setValidationCount(validation.size());
    model.setTestCount(test.size());
    model.setArtifactJson(artifact);
    model.setMetricsJson(metricsJson);
    model.setChampionComparisonJson(comparison);
    model.setDatasetChecksum(dataset.getChecksum());
    model.setLabelPolicyVersion(dataset.getLabelPolicyVersion());
    model.setFeatureVersion(dataset.getFeatureVersion());
    model.setCreatedBy(actor);
    models.save(model);
    audit.record(
        actor,
        LearningEventType.CANDIDATE_TRAINED,
        dataset.getId(),
        model.getId(),
        "Candidate "
            + modelVersion
            + " verify="
            + verify
            + " stop="
            + stop
            + " testF1="
            + testMetrics.f1());
    log.info(
        "Calibration candidate trained: version={} verify={} stop={} testF1={}",
        modelVersion,
        verify,
        stop,
        testMetrics.f1());
    return new TrainingResultDto(
        modelVersion,
        model.getStatus().name(),
        true,
        null,
        verify,
        stop,
        testMetrics.precision(),
        testMetrics.recall(),
        testMetrics.f1(),
        testMetrics.accuracy(),
        testMetrics.falsePositiveRate(),
        testMetrics.falseNegativeRate(),
        validation.size(),
        test.size(),
        comparison);
  }

  @Transactional
  public ModelDto approve(UUID modelId, UUID actor) {
    LearningModel model = requireLocked(modelId);
    if (!model.getStatus().canApprove()) {
      throw conflict("Only a CANDIDATE can be approved (status=" + model.getStatus() + ")");
    }
    model.setStatus(ModelStatus.APPROVED);
    model.setApprovedBy(actor);
    model.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
    models.save(model);
    audit.record(
        actor, LearningEventType.CANDIDATE_APPROVED, model.getDatasetId(), model.getId(),
        "Approved " + model.getModelVersion());
    return toDto(model);
  }

  @Transactional
  public ModelDto reject(UUID modelId, UUID actor) {
    LearningModel model = requireLocked(modelId);
    if (model.getStatus() != ModelStatus.CANDIDATE
        && model.getStatus() != ModelStatus.APPROVED) {
      throw conflict("Only a CANDIDATE or APPROVED model can be rejected");
    }
    model.setStatus(ModelStatus.REJECTED);
    models.save(model);
    audit.record(
        actor, LearningEventType.CANDIDATE_REJECTED, model.getDatasetId(), model.getId(),
        "Rejected " + model.getModelVersion());
    return toDto(model);
  }

  @Transactional
  public ModelDto deploy(UUID modelId, UUID actor) {
    LearningModel model = requireLocked(modelId);
    if (!model.getStatus().canDeploy()) {
      throw conflict("Only an APPROVED model can be deployed (status=" + model.getStatus() + ")");
    }
    LearningDataset dataset = requireReadyDataset(model.getDatasetId());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    List<LearningModel> active = models.findActiveByType(LearningRiskCalibrationProvider.MODEL_TYPE);
    LearningModel previous = null;
    for (LearningModel champion : active) {
      if (champion.getId().equals(model.getId())) {
        continue;
      }
      champion.setStatus(ModelStatus.RETIRED);
      champion.setRetiredAt(now);
      models.save(champion);
      previous = champion;
      audit.record(
          actor, LearningEventType.MODEL_RETIRED, champion.getDatasetId(), champion.getId(),
          "Retired by deployment of " + model.getModelVersion());
    }

    model.setStatus(ModelStatus.ACTIVE);
    model.setActivatedBy(actor);
    model.setActivatedAt(now);
    model.setRetiredAt(null);
    model.setRollbackReference(previous == null ? null : previous.getId());
    models.save(model);
    audit.record(
        actor,
        LearningEventType.MODEL_DEPLOYED,
        dataset.getId(),
        model.getId(),
        "Deployed "
            + model.getModelVersion()
            + (previous == null
                ? " (no previous champion)"
                : " replacing " + previous.getModelVersion()));
    log.info("Calibration deployed: version={}", model.getModelVersion());
    return toDto(model);
  }

  @Transactional
  public ModelDto rollback(UUID modelId, UUID actor) {
    LearningModel model = requireLocked(modelId);
    if (model.getStatus() != ModelStatus.ACTIVE) {
      throw conflict("Only the ACTIVE model can be rolled back");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    model.setStatus(ModelStatus.ROLLED_BACK);
    model.setRetiredAt(now);
    models.save(model);

    LearningModel restored = null;
    if (model.getRollbackReference() != null) {
      Optional<LearningModel> previous = models.findById(model.getRollbackReference());
      if (previous.isPresent() && datasetIsReady(previous.get().getDatasetId())) {
        restored = previous.get();
        restored.setStatus(ModelStatus.ACTIVE);
        restored.setActivatedBy(actor);
        restored.setActivatedAt(now);
        restored.setRetiredAt(null);
        models.save(restored);
      }
    }
    audit.record(
        actor,
        LearningEventType.MODEL_ROLLED_BACK,
        model.getDatasetId(),
        model.getId(),
        "Rolled back " + model.getModelVersion() + (restored == null
            ? " to deterministic defaults"
            : " to " + restored.getModelVersion()));
    return toDto(restored == null ? model : restored);
  }

  @Transactional(readOnly = true)
  public List<ModelDto> list() {
    return models.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public List<EventDto> events() {
    return events.findTop100ByOrderByOccurredAtDesc().stream()
        .map(
            event ->
                new EventDto(
                    event.getEventType().name(),
                    event.getDatasetId() == null ? null : event.getDatasetId().toString(),
                    event.getModelId() == null ? null : event.getModelId().toString(),
                    event.getDetail(),
                    event.getOccurredAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public OverviewDto overview() {
    RiskCalibration active = calibrationProvider.activeCalibration();
    Optional<LearningModel> activeModel =
        models.findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
            ModelStatus.ACTIVE, LearningRiskCalibrationProvider.MODEL_TYPE);
    String datasetVersion =
        activeModel
            .flatMap(model -> datasets.findById(model.getDatasetId()))
            .map(LearningDataset::getDatasetVersion)
            .orElse(null);
    ActiveCalibrationDto calibration =
        new ActiveCalibrationDto(
            active.modelVersion(),
            datasetVersion,
            active.verifyThreshold(),
            active.stopThreshold(),
            activeModel.isPresent());
    return new OverviewDto(
        datasets.count(),
        datasets.countByStatus(DatasetStatus.READY),
        datasets.countByStatus(DatasetStatus.INSUFFICIENT_DATA),
        datasets.countByStatus(DatasetStatus.INVALIDATED),
        models.count(),
        models.countByStatus(ModelStatus.CANDIDATE),
        models.countByStatus(ModelStatus.ACTIVE),
        calibration,
        events());
  }

  @Transactional(readOnly = true)
  public ModelDto get(UUID modelId) {
    return toDto(
        models
            .findById(modelId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown model")));
  }

  private TrainingResultDto failure(LearningDataset dataset, UUID actor, String reason) {
    String version = nextVersion(dataset);
    LearningModel model = new LearningModel();
    model.setModelVersion(version);
    model.setDatasetId(dataset.getId());
    model.setModelType(LearningRiskCalibrationProvider.MODEL_TYPE);
    model.setStatus(ModelStatus.REJECTED);
    model.setArtifactJson("{}");
    model.setDatasetChecksum(dataset.getChecksum());
    model.setLabelPolicyVersion(dataset.getLabelPolicyVersion());
    model.setFeatureVersion(dataset.getFeatureVersion());
    model.setTrainingFailureReason(LearningAuditService.sanitize(reason));
    model.setCreatedBy(actor);
    models.save(model);
    audit.record(
        actor,
        LearningEventType.TRAINING_FAILED,
        dataset.getId(),
        model.getId(),
        "Training failed for " + dataset.getDatasetVersion() + ": " + reason);
    log.warn("Training refused for dataset {}: {}", dataset.getDatasetVersion(), reason);
    return new TrainingResultDto(
        version, ModelStatus.REJECTED.name(), false, reason, null, null, null, null, null, null,
        null, null, 0, 0, null);
  }

  private LearningDataset requireReadyDataset(UUID datasetId) {
    LearningDataset dataset =
        datasets
            .findById(datasetId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown dataset"));
    if (dataset.getStatus() != DatasetStatus.READY) {
      throw conflict("Dataset is " + dataset.getStatus() + " and cannot be deployed");
    }
    return dataset;
  }

  private boolean datasetIsReady(UUID datasetId) {
    return datasets.findById(datasetId).map(d -> d.getStatus() == DatasetStatus.READY).orElse(false);
  }

  private LearningModel requireLocked(UUID modelId) {
    return models
        .findByIdForUpdate(modelId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown model"));
  }

  private String nextVersion(LearningDataset dataset) {
    String prefix = "calib-v1-" + dataset.getChecksum().substring(0, 12) + "-";
    long sequence = models.findByDatasetId(dataset.getId()).size() + 1;
    String version = prefix + sequence;
    while (models.findByModelVersion(version).isPresent()) {
      sequence++;
      version = prefix + sequence;
    }
    return version;
  }

  private static boolean hasBothLabels(List<LearningDatasetItem> rows) {
    boolean fraud = false;
    boolean legitimate = false;
    for (LearningDatasetItem row : rows) {
      if (FeedbackLabel.FRAUD.name().equals(row.getLabel())) {
        fraud = true;
      } else if (FeedbackLabel.LEGITIMATE.name().equals(row.getLabel())) {
        legitimate = true;
      }
    }
    return fraud && legitimate;
  }

  private static int chooseStop(List<LearningDatasetItem> train) {
    int bestStop = DEFAULT_STOP;
    int bestCost = Integer.MAX_VALUE;
    double bestF1 = -1;
    for (int stop = 2; stop <= 100; stop++) {
      Metrics metrics = stopMetrics(train, stop);
      int cost =
          FALSE_NEGATIVE_COST * metrics.falseNegatives()
              + FALSE_POSITIVE_COST * metrics.falsePositives();
      if (cost < bestCost
          || (cost == bestCost && metrics.f1() > bestF1)
          || (cost == bestCost && metrics.f1() == bestF1 && stop > bestStop)) {
        bestCost = cost;
        bestF1 = metrics.f1();
        bestStop = stop;
      }
    }
    return bestStop;
  }

  private static int chooseVerify(List<LearningDatasetItem> train, int stop) {
    if (stop <= 2) {
      return 1;
    }
    int bestVerify = 1;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int verify = 1; verify < stop; verify++) {
      int tp = 0;
      int fn = 0;
      int fp = 0;
      int tn = 0;
      for (LearningDatasetItem row : train) {
        boolean fraud = FeedbackLabel.FRAUD.name().equals(row.getLabel());
        boolean flagged = row.getRiskScore() != null && row.getRiskScore() >= verify;
        if (fraud && flagged) {
          tp++;
        } else if (fraud) {
          fn++;
        } else if (flagged) {
          fp++;
        } else {
          tn++;
        }
      }
      double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
      double fpr = fp + tn == 0 ? 0 : (double) fp / (fp + tn);
      double score = 2 * recall - fpr;
      if (score > bestScore || (score == bestScore && verify > bestVerify)) {
        bestScore = score;
        bestVerify = verify;
      }
    }
    return bestVerify;
  }

  private static Metrics stopMetrics(List<LearningDatasetItem> rows, int stop) {
    int tp = 0;
    int fp = 0;
    int tn = 0;
    int fn = 0;
    for (LearningDatasetItem row : rows) {
      boolean fraud = FeedbackLabel.FRAUD.name().equals(row.getLabel());
      boolean stopped = row.getRiskScore() != null && row.getRiskScore() >= stop;
      if (fraud && stopped) {
        tp++;
      } else if (fraud) {
        fn++;
      } else if (stopped) {
        fp++;
      } else {
        tn++;
      }
    }
    return new Metrics(tp, fp, tn, fn);
  }

  private static Metrics metrics(List<LearningDatasetItem> rows, int verify) {
    int tp = 0;
    int fp = 0;
    int tn = 0;
    int fn = 0;
    for (LearningDatasetItem row : rows) {
      boolean fraud = FeedbackLabel.FRAUD.name().equals(row.getLabel());
      boolean flagged = row.getRiskScore() != null && row.getRiskScore() >= verify;
      if (fraud && flagged) {
        tp++;
      } else if (fraud) {
        fn++;
      } else if (flagged) {
        fp++;
      } else {
        tn++;
      }
    }
    return new Metrics(tp, fp, tn, fn);
  }

  private static int decisionCost(List<LearningDatasetItem> rows, int verify) {
    Metrics metrics = metrics(rows, verify);
    return FALSE_NEGATIVE_COST * metrics.falseNegatives()
        + FALSE_POSITIVE_COST * metrics.falsePositives();
  }

  private ModelDto toDto(LearningModel model) {
    String datasetVersion = null;
    if (model.getDatasetId() != null) {
      datasetVersion =
          datasets.findById(model.getDatasetId()).map(LearningDataset::getDatasetVersion).orElse(null);
    }
    Integer verify = null;
    Integer stop = null;
    if (model.getArtifactJson() != null && !"{}".equals(model.getArtifactJson())) {
      try {
        var artifact = JSON.readTree(model.getArtifactJson());
        verify = artifact.has("verifyThreshold") ? artifact.get("verifyThreshold").asInt() : null;
        stop = artifact.has("stopThreshold") ? artifact.get("stopThreshold").asInt() : null;
      } catch (Exception ignored) {
        // Artifact is optional for rejected/failed candidates.
      }
    }
    return new ModelDto(
        model.getId() == null ? null : model.getId().toString(),
        model.getModelVersion(),
        model.getDatasetId() == null ? null : model.getDatasetId().toString(),
        datasetVersion,
        model.getModelType(),
        model.getStatus().name(),
        verify,
        stop,
        model.getPrecisionValue(),
        model.getRecallValue(),
        model.getF1Value(),
        model.getAccuracyValue(),
        model.getFalsePositiveRate(),
        model.getFalseNegativeRate(),
        model.getValidationCount(),
        model.getTestCount(),
        model.getDatasetChecksum(),
        model.getLabelPolicyVersion(),
        model.getFeatureVersion(),
        model.getTrainingFailureReason(),
        model.getChampionComparisonJson(),
        model.getRollbackReference() == null ? null : model.getRollbackReference().toString(),
        model.getCreatedAt(),
        model.getApprovedAt(),
        model.getActivatedAt(),
        model.getRetiredAt());
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private static double round(double value) {
    return Math.round(value * 10_000d) / 10_000d;
  }

  private record Metrics(int truePositives, int falsePositives, int trueNegatives, int falseNegatives) {
    double precision() {
      int denominator = truePositives + falsePositives;
      return denominator == 0 ? 0 : (double) truePositives / denominator;
    }

    double recall() {
      int denominator = truePositives + falseNegatives;
      return denominator == 0 ? 0 : (double) truePositives / denominator;
    }

    double f1() {
      double precision = precision();
      double recall = recall();
      return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
    }

    double accuracy() {
      int total = truePositives + falsePositives + trueNegatives + falseNegatives;
      return total == 0 ? 0 : (double) (truePositives + trueNegatives) / total;
    }

    double falsePositiveRate() {
      int denominator = falsePositives + trueNegatives;
      return denominator == 0 ? 0 : (double) falsePositives / denominator;
    }

    double falseNegativeRate() {
      int denominator = falseNegatives + truePositives;
      return denominator == 0 ? 0 : (double) falseNegatives / denominator;
    }
  }
}
