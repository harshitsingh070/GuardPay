package com.guardpay.learning.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Read/response contracts for the admin learning console. */
public final class LearningDtos {

  private LearningDtos() {}

  public record DatasetDto(
      String id,
      String datasetVersion,
      String status,
      String featureVersion,
      String labelPolicyVersion,
      String checksum,
      int recordCount,
      int fraudCount,
      int legitimateCount,
      int trainCount,
      int validationCount,
      int testCount,
      int excludedCount,
      OffsetDateTime builtAt,
      OffsetDateTime invalidatedAt,
      String invalidationReason,
      List<String> unmetRequirements) {}

  public record TrainingResultDto(
      String modelVersion,
      String status,
      boolean trained,
      String failureReason,
      Integer verifyThreshold,
      Integer stopThreshold,
      Double precision,
      Double recall,
      Double f1,
      Double accuracy,
      Double falsePositiveRate,
      Double falseNegativeRate,
      int validationCount,
      int testCount,
      String championComparisonJson) {}

  public record ModelDto(
      String id,
      String modelVersion,
      String datasetId,
      String datasetVersion,
      String modelType,
      String status,
      Integer verifyThreshold,
      Integer stopThreshold,
      Double precision,
      Double recall,
      Double f1,
      Double accuracy,
      Double falsePositiveRate,
      Double falseNegativeRate,
      int validationCount,
      int testCount,
      String datasetChecksum,
      String labelPolicyVersion,
      String featureVersion,
      String trainingFailureReason,
      String championComparisonJson,
      String rollbackReference,
      OffsetDateTime createdAt,
      OffsetDateTime approvedAt,
      OffsetDateTime activatedAt,
      OffsetDateTime retiredAt) {}

  public record ExclusionDto(
      String feedbackId, String reasonCode, String detail, OffsetDateTime createdAt) {}

  public record EventDto(
      String eventType,
      String datasetId,
      String modelId,
      String detail,
      OffsetDateTime occurredAt) {}

  public record ActiveCalibrationDto(
      String modelVersion,
      String datasetVersion,
      Integer verifyThreshold,
      Integer stopThreshold,
      boolean deployed) {}

  public record OverviewDto(
      long totalDatasets,
      long readyDatasets,
      long insufficientDatasets,
      long invalidatedDatasets,
      long totalModels,
      long candidateModels,
      long activeModels,
      ActiveCalibrationDto activeCalibration,
      List<EventDto> recentEvents) {}
}
