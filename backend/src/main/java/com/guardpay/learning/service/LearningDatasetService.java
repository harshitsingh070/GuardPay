package com.guardpay.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.feedback.FeedbackLabel;
import com.guardpay.feedback.FeedbackStatus;
import com.guardpay.feedback.FraudFeedback;
import com.guardpay.feedback.FraudFeedbackRepository;
import com.guardpay.learning.domain.DatasetStatus;
import com.guardpay.learning.domain.LearningDataset;
import com.guardpay.learning.domain.LearningDatasetExclusion;
import com.guardpay.learning.domain.LearningDatasetItem;
import com.guardpay.learning.domain.LearningEventType;
import com.guardpay.learning.repository.LearningDatasetExclusionRepository;
import com.guardpay.learning.repository.LearningDatasetItemRepository;
import com.guardpay.learning.repository.LearningDatasetRepository;
import com.guardpay.learning.web.dto.LearningDtos.DatasetDto;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
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
 * Builds immutable, checksum-addressed datasets from admin-verified feedback.
 *
 * <p>Features are reconstructed only from the decision-time
 * {@link RiskAssessment}. The label is stored in its own column and is never
 * written into the feature JSON, so there is no label leakage.
 */
@Service
public class LearningDatasetService {

  public static final String FEATURE_VERSION = "risk-assessment-v1";
  public static final String LABEL_POLICY_VERSION = "verified-labels-v1";
  public static final int MIN_TOTAL = 20;
  public static final int MIN_FRAUD = 5;
  public static final int MIN_LEGITIMATE = 5;

  private static final Logger log = LoggerFactory.getLogger(LearningDatasetService.class);
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private final FraudFeedbackRepository feedback;
  private final RiskAssessmentRepository assessments;
  private final LearningDatasetRepository datasets;
  private final LearningDatasetItemRepository items;
  private final LearningDatasetExclusionRepository exclusions;
  private final LearningAuditService audit;

  public LearningDatasetService(
      FraudFeedbackRepository feedback,
      RiskAssessmentRepository assessments,
      LearningDatasetRepository datasets,
      LearningDatasetItemRepository items,
      LearningDatasetExclusionRepository exclusions,
      LearningAuditService audit) {
    this.feedback = feedback;
    this.assessments = assessments;
    this.datasets = datasets;
    this.items = items;
    this.exclusions = exclusions;
    this.audit = audit;
  }

  @Transactional
  public DatasetDto build(UUID actor) {
    List<FraudFeedback> verified =
        feedback.findByFeedbackStatusInOrderByCreatedAtAsc(
            List.of(FeedbackStatus.VERIFIED_FRAUD, FeedbackStatus.VERIFIED_LEGITIMATE));

    List<Candidate> candidates = new ArrayList<>();
    List<Exclusion> rejected = new ArrayList<>();
    for (FraudFeedback row : verified) {
      Candidate candidate = candidate(row, rejected);
      if (candidate != null) {
        candidates.add(candidate);
      }
    }
    candidates.sort(
        Comparator.comparing(Candidate::occurredAt).thenComparing(c -> c.feedbackId));

    int fraudCount = (int) candidates.stream().filter(c -> c.label == FeedbackLabel.FRAUD).count();
    int legitimateCount = candidates.size() - fraudCount;
    List<String> unmet = unmetRequirements(candidates.size(), fraudCount, legitimateCount);
    DatasetStatus status = unmet.isEmpty() ? DatasetStatus.READY : DatasetStatus.INSUFFICIENT_DATA;

    String checksum = checksum(candidates);
    Optional<LearningDataset> existing = datasets.findByChecksum(checksum);
    if (existing.isPresent()) {
      return toDto(existing.get());
    }

    Split split = split(candidates.size());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    LearningDataset dataset = new LearningDataset();
    dataset.setDatasetVersion("dataset-v1-" + checksum.substring(0, 12));
    dataset.setStatus(status);
    dataset.setFeatureVersion(FEATURE_VERSION);
    dataset.setLabelPolicyVersion(LABEL_POLICY_VERSION);
    dataset.setChecksum(checksum);
    dataset.setRecordCount(candidates.size());
    dataset.setFraudCount(fraudCount);
    dataset.setLegitimateCount(legitimateCount);
    dataset.setTrainCount(split.trainCount());
    dataset.setValidationCount(split.validationCount());
    dataset.setTestCount(split.testCount());
    dataset.setExcludedCount(rejected.size());
    dataset.setCreatedBy(actor);
    dataset.setBuiltAt(now);
    LearningDataset saved = datasets.save(dataset);

    List<LearningDatasetItem> rows = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      Candidate candidate = candidates.get(i);
      LearningDatasetItem item = new LearningDatasetItem();
      item.setDatasetId(saved.getId());
      item.setFeedbackId(candidate.feedbackId);
      item.setTransactionId(candidate.transactionId);
      item.setLabel(candidate.label.name());
      item.setSplitName(split.nameFor(i));
      item.setDecisionAction(candidate.decisionAction);
      item.setRiskScore(candidate.riskScore);
      item.setCorrelationContribution(candidate.correlationContribution);
      item.setRecipientReputationPoints(candidate.reputationPoints);
      item.setFindingCodes(String.join(",", candidate.findingCodes));
      item.setFeatureJson(candidate.featureJson);
      item.setOccurredAt(candidate.occurredAt);
      rows.add(item);
    }
    items.saveAll(rows);

    List<LearningDatasetExclusion> exclusionRows = new ArrayList<>();
    for (Exclusion exclusion : rejected) {
      LearningDatasetExclusion entity = new LearningDatasetExclusion();
      entity.setDatasetId(saved.getId());
      entity.setFeedbackId(exclusion.feedbackId);
      entity.setReasonCode(exclusion.reasonCode);
      entity.setDetail(exclusion.detail);
      exclusionRows.add(entity);
    }
    exclusions.saveAll(exclusionRows);

    audit.record(
        actor,
        LearningEventType.DATASET_BUILT,
        saved.getId(),
        null,
        "Dataset "
            + saved.getDatasetVersion()
            + " status="
            + status
            + " records="
            + candidates.size()
            + " fraud="
            + fraudCount
            + " legitimate="
            + legitimateCount
            + " excluded="
            + rejected.size()
            + (unmet.isEmpty() ? "" : " unmet=" + String.join("; ", unmet)));
    log.info(
        "Learning dataset built: version={} status={} records={}",
        saved.getDatasetVersion(),
        status,
        candidates.size());
    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public List<DatasetDto> list() {
    return datasets.findAllByOrderByBuiltAtDesc().stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public DatasetDto get(UUID datasetId) {
    return toDto(require(datasetId));
  }

  @Transactional(readOnly = true)
  public LearningDataset require(UUID datasetId) {
    return datasets
        .findById(datasetId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown dataset"));
  }

  @Transactional(readOnly = true)
  public List<LearningDatasetExclusion> exclusions(UUID datasetId) {
    return exclusions.findByDatasetIdOrderByCreatedAtAsc(datasetId);
  }

  public DatasetDto toDto(LearningDataset dataset) {
    return new DatasetDto(
        dataset.getId() == null ? null : dataset.getId().toString(),
        dataset.getDatasetVersion(),
        dataset.getStatus().name(),
        dataset.getFeatureVersion(),
        dataset.getLabelPolicyVersion(),
        dataset.getChecksum(),
        dataset.getRecordCount(),
        dataset.getFraudCount(),
        dataset.getLegitimateCount(),
        dataset.getTrainCount(),
        dataset.getValidationCount(),
        dataset.getTestCount(),
        dataset.getExcludedCount(),
        dataset.getBuiltAt(),
        dataset.getInvalidatedAt(),
        dataset.getInvalidationReason(),
        unmetRequirements(
            dataset.getRecordCount(), dataset.getFraudCount(), dataset.getLegitimateCount()));
  }

  @Transactional
  public void invalidate(LearningDataset dataset, String reason, UUID actor) {
    if (dataset.getStatus() == DatasetStatus.INVALIDATED) {
      return;
    }
    dataset.setStatus(DatasetStatus.INVALIDATED);
    dataset.setInvalidatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    dataset.setInvalidationReason(LearningAuditService.sanitize(reason));
    datasets.save(dataset);
    audit.record(
        actor,
        LearningEventType.DATASET_INVALIDATED,
        dataset.getId(),
        null,
        dataset.getInvalidationReason());
  }

  public static List<String> unmetRequirements(int total, int fraud, int legitimate) {
    List<String> unmet = new ArrayList<>();
    if (total < MIN_TOTAL) {
      unmet.add("need at least " + MIN_TOTAL + " verified records, found " + total);
    }
    if (fraud < MIN_FRAUD) {
      unmet.add("need at least " + MIN_FRAUD + " verified fraud labels, found " + fraud);
    }
    if (legitimate < MIN_LEGITIMATE) {
      unmet.add(
          "need at least " + MIN_LEGITIMATE + " verified legitimate labels, found " + legitimate);
    }
    return unmet;
  }

  private Candidate candidate(FraudFeedback row, List<Exclusion> rejected) {
    if (row.getLabel() == null) {
      rejected.add(new Exclusion(row.getId(), "MISSING_LABEL", "Feedback has no label"));
      return null;
    }
    RiskAssessment assessment = assessmentFor(row);
    if (assessment == null) {
      rejected.add(
          new Exclusion(row.getId(), "NO_DECISION_ASSESSMENT", "No persisted risk assessment"));
      return null;
    }
    Transaction transaction = assessment.getTransaction();
    if (transaction == null) {
      rejected.add(
          new Exclusion(row.getId(), "NO_TRANSACTION", "Assessment has no transaction"));
      return null;
    }
    List<String> findingCodes = findingCodes(assessment.getFindings());
    OffsetDateTime occurredAt =
        transaction.getTransactionTimestamp() == null
            ? row.getCreatedAt()
            : transaction.getTransactionTimestamp();
    if (occurredAt == null) {
      occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
    Map<String, Object> features = new LinkedHashMap<>();
    features.put("riskScore", assessment.getScore());
    features.put("decisionAction", name(assessment.getRecommendedAction()));
    features.put("correlationContribution", assessment.getCorrelationContribution());
    features.put("recipientReputationPoints", assessment.getRecipientReputationPoints());
    features.put("findingCodes", findingCodes);
    features.put("amount", transaction.getAmount() == null ? null : transaction.getAmount().toPlainString());
    features.put("currency", transaction.getCurrency());
    features.put("transactionTimestamp", occurredAt.toString());
    String featureJson;
    try {
      featureJson = JSON.writeValueAsString(features);
    } catch (Exception failure) {
      rejected.add(
          new Exclusion(row.getId(), "FEATURE_SERIALIZATION_FAILED", "Feature JSON was invalid"));
      return null;
    }
    return new Candidate(
        row.getId(),
        row.getTransactionId(),
        row.getLabel(),
        assessment.getScore(),
        name(assessment.getRecommendedAction()),
        assessment.getCorrelationContribution(),
        assessment.getRecipientReputationPoints(),
        findingCodes,
        featureJson,
        occurredAt);
  }

  private RiskAssessment assessmentFor(FraudFeedback row) {
    if (row.getRiskAssessmentId() != null) {
      RiskAssessment byId = assessments.findById(row.getRiskAssessmentId()).orElse(null);
      if (byId != null) {
        return byId;
      }
    }
    if (row.getTransactionId() == null) {
      return null;
    }
    return assessments.findByTransactionIdInOrderByCreatedAtDesc(List.of(row.getTransactionId()))
        .stream()
        .findFirst()
        .orElse(null);
  }

  private static List<String> findingCodes(String findingsJson) {
    if (findingsJson == null || findingsJson.isBlank()) {
      return List.of();
    }
    try {
      JsonNode root = JSON.readTree(findingsJson);
      List<String> codes = new ArrayList<>();
      if (root.isArray()) {
        for (JsonNode node : root) {
          JsonNode code = node.get("code");
          if (code != null && code.isTextual()) {
            codes.add(code.asText());
          }
        }
      }
      return codes;
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private static String checksum(List<Candidate> candidates) {
    StringBuilder canonical = new StringBuilder();
    canonical.append(FEATURE_VERSION).append('|').append(LABEL_POLICY_VERSION).append('\n');
    for (Candidate candidate : candidates) {
      canonical
          .append(candidate.feedbackId)
          .append('|')
          .append(candidate.label)
          .append('|')
          .append(candidate.occurredAt)
          .append('|')
          .append(candidate.featureJson)
          .append('\n');
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception failure) {
      throw new IllegalStateException("SHA-256 unavailable", failure);
    }
  }

  /** Chronological 70/15/15 split; every non-empty split keeps at least one row. */
  private static Split split(int size) {
    if (size == 0) {
      return new Split(0, 0, 0);
    }
    if (size == 1) {
      return new Split(1, 0, 0);
    }
    if (size == 2) {
      return new Split(1, 1, 0);
    }
    int trainEnd = Math.max(1, Math.min((int) Math.floor(size * 0.70), size - 2));
    int validationEnd = Math.max(trainEnd + 1, (int) Math.floor(size * 0.85));
    validationEnd = Math.min(validationEnd, size - 1);
    return new Split(trainEnd, validationEnd - trainEnd, size - validationEnd);
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private record Candidate(
      UUID feedbackId,
      UUID transactionId,
      FeedbackLabel label,
      int riskScore,
      String decisionAction,
      int correlationContribution,
      int reputationPoints,
      List<String> findingCodes,
      String featureJson,
      OffsetDateTime occurredAt) {}

  private record Exclusion(UUID feedbackId, String reasonCode, String detail) {}

  private record Split(int trainCount, int validationCount, int testCount) {
    String nameFor(int index) {
      if (index < trainCount) {
        return "TRAIN";
      }
      if (index < trainCount + validationCount) {
        return "VALIDATION";
      }
      return "TEST";
    }
  }
}
