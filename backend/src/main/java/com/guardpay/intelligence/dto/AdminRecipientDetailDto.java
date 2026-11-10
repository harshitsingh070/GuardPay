package com.guardpay.intelligence.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Full admin view of one global recipient entity. */
public record AdminRecipientDetailDto(
    UUID recipientReference,
    String reputationStatus,
    String category,
    int score,
    int evidenceCount,
    int reportCount,
    int distinctReporterCount,
    String identityConfidence,
    OffsetDateTime lastEvidenceAt,
    OffsetDateTime lastCalculatedAt,
    String calculationVersion,
    List<AdminEvidenceDto> evidence,
    List<AdminReportDto> reports,
    List<ReviewEventDto> reviewEvents) {

  public AdminRecipientDetailDto {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    reports = reports == null ? List.of() : List.copyOf(reports);
    reviewEvents = reviewEvents == null ? List.of() : List.copyOf(reviewEvents);
  }
}
