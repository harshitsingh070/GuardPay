package com.guardpay.intelligence.service;

import com.guardpay.intelligence.config.RecipientIntelligenceProperties;
import com.guardpay.intelligence.domain.FraudIntelligenceReport;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.RecipientFraudEvidence;
import com.guardpay.intelligence.domain.RecipientIntelligenceAccessLog;
import com.guardpay.intelligence.domain.ReportStatus;
import com.guardpay.intelligence.dto.AdminEvidenceDto;
import com.guardpay.intelligence.dto.AdminRecipientDetailDto;
import com.guardpay.intelligence.dto.AdminReportDto;
import com.guardpay.intelligence.dto.IntelligenceAggregateDto;
import com.guardpay.intelligence.dto.MyReportDto;
import com.guardpay.intelligence.dto.RecipientReputationSummaryDto;
import com.guardpay.intelligence.dto.ReviewEventDto;
import com.guardpay.intelligence.repository.FraudIntelligenceReportRepository;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.repository.RecipientFraudEvidenceRepository;
import com.guardpay.intelligence.repository.RecipientIntelligenceAccessLogRepository;
import com.guardpay.intelligence.repository.RecipientReviewEventRepository;
import com.guardpay.intelligence.repository.RecipientRelationshipRepository;
import com.guardpay.intelligence.service.RecipientIdentityService.IdentityMatch;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-side intelligence queries.
 *
 * <p>Non-admin summaries are authorized by an existing relationship with the
 * recipient (a payment or a report). Unauthorized lookups return 404 so the
 * endpoint cannot be used to enumerate recipients.
 */
@Service
public class RecipientIntelligenceQueryService {

  static final int MAX_PAGE_SIZE = 100;
  private static final String DISCLAIMER =
      "Global reputation is evidence-only; the deterministic risk engine remains the "
          + "sole decision authority.";

  private final RecipientIntelligenceProperties properties;
  private final RecipientIdentityService identity;
  private final GlobalRecipientEntityRepository entities;
  private final FraudIntelligenceReportRepository reports;
  private final RecipientFraudEvidenceRepository evidence;
  private final RecipientReviewEventRepository reviewEvents;
  private final RecipientIntelligenceAccessLogRepository accessLogs;
  private final RecipientRelationshipRepository relationships;
  private final RecipientRepository recipients;

  public RecipientIntelligenceQueryService(
      RecipientIntelligenceProperties properties,
      RecipientIdentityService identity,
      GlobalRecipientEntityRepository entities,
      FraudIntelligenceReportRepository reports,
      RecipientFraudEvidenceRepository evidence,
      RecipientReviewEventRepository reviewEvents,
      RecipientIntelligenceAccessLogRepository accessLogs,
      RecipientRelationshipRepository relationships,
      RecipientRepository recipients) {
    this.properties = properties;
    this.identity = identity;
    this.entities = entities;
    this.reports = reports;
    this.evidence = evidence;
    this.reviewEvents = reviewEvents;
    this.accessLogs = accessLogs;
    this.relationships = relationships;
    this.recipients = recipients;
  }

  @Transactional(readOnly = true)
  public RecipientReputationSummaryDto summary(User user, UUID entityId) {
    requireConfigured();
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    GlobalRecipientEntity entity =
        entities
            .findById(entityId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient"));
    Optional<IdentityMatch> match = authorizedMatch(user, entity);
    if (match.isEmpty() && !user.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient");
    }
    recordAccess(user, entity, "SUMMARY", "AUTHORIZED");
    return new RecipientReputationSummaryDto(
        entity.getId(),
        match.isPresent() ? "MATCH_FOUND" : "NO_MATCH",
        entity.getReputationStatus().name(),
        entity.getReputationCategory().name(),
        entity.getReputationScore(),
        entity.getEvidenceCount(),
        entity.getReportCount(),
        match.map(value -> value.matchLevel().name()).orElse("NO_MATCH"),
        match.map(value -> value.confidence().name()).orElse(entity.getIdentityConfidence().name()),
        entity.getLastEvidenceAt(),
        DISCLAIMER);
  }

  @Transactional(readOnly = true)
  public Page<MyReportDto> myReports(User user, int page, int size) {
    Pageable pageable = pageable(page, size);
    return reports
        .findByReporterReferenceOrderByCreatedAtDesc(user.getId(), pageable)
        .map(
            report ->
                new MyReportDto(
                    report.getId(),
                    report.getRecipientEntity().getId(),
                    report.getReportStatus().name(),
                    report.getCreatedAt(),
                    report.getIncidentAt(),
                    report.getReviewedAt(),
                    report.getReviewNote()));
  }

  @Transactional(readOnly = true)
  public MyReportDto myReport(User user, UUID reportId) {
    FraudIntelligenceReport report =
        reports
            .findByIdAndReporterReference(reportId, user.getId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown report"));
    return new MyReportDto(
        report.getId(),
        report.getRecipientEntity().getId(),
        report.getReportStatus().name(),
        report.getCreatedAt(),
        report.getIncidentAt(),
        report.getReviewedAt(),
        report.getReviewNote());
  }

  @Transactional(readOnly = true)
  public IntelligenceAggregateDto aggregate(User user) {
    long total = reports.countByReporterReference(user.getId());
    long submitted =
        reports.countByReporterReferenceAndReportStatus(user.getId(), ReportStatus.SUBMITTED);
    long underReview =
        reports.countByReporterReferenceAndReportStatus(user.getId(), ReportStatus.UNDER_REVIEW);
    long accepted =
        reports.countByReporterReferenceAndReportStatus(user.getId(), ReportStatus.ACCEPTED);
    long rejected =
        reports.countByReporterReferenceAndReportStatus(user.getId(), ReportStatus.REJECTED);
    long distinct = reports.countDistinctRecipientsByReporter(user.getId());
    return new IntelligenceAggregateDto(
        total, submitted, underReview, accepted, rejected, distinct);
  }

  @Transactional(readOnly = true)
  public AdminRecipientDetailDto adminDetail(UUID entityId) {
    GlobalRecipientEntity entity =
        entities
            .findById(entityId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient"));
    List<AdminEvidenceDto> evidenceRows =
        evidence.findByRecipientEntityIdOrderByObservedAtDesc(entityId).stream()
            .map(RecipientIntelligenceQueryService::toEvidenceDto)
            .toList();
    List<AdminReportDto> reportRows =
        reports.findByRecipientEntityIdOrderByCreatedAtDesc(entityId).stream()
            .map(RecipientIntelligenceQueryService::toReportDto)
            .toList();
    List<ReviewEventDto> events =
        reviewEvents.findByRecipientEntityIdOrderByCreatedAtDesc(entityId).stream()
            .map(
                event ->
                    new ReviewEventDto(
                        event.getAction().name(),
                        event.getFromStatus() == null ? null : event.getFromStatus().name(),
                        event.getToStatus().name(),
                        event.getNote(),
                        event.getCreatedAt()))
            .toList();
    return new AdminRecipientDetailDto(
        entity.getId(),
        entity.getReputationStatus().name(),
        entity.getReputationCategory().name(),
        entity.getReputationScore(),
        entity.getEvidenceCount(),
        entity.getReportCount(),
        entity.getDistinctReporterCount(),
        entity.getIdentityConfidence().name(),
        entity.getLastEvidenceAt(),
        entity.getLastCalculatedAt(),
        entity.getCalculationVersion(),
        evidenceRows,
        reportRows,
        events);
  }

  @Transactional(readOnly = true)
  public Page<AdminReportDto> adminReports(ReportStatus status, int page, int size) {
    Pageable pageable = pageable(page, size);
    Page<FraudIntelligenceReport> result =
        status == null
            ? reports.findAll(pageable)
            : reports.findByReportStatusOrderByCreatedAtDesc(status, pageable);
    return result.map(RecipientIntelligenceQueryService::toReportDto);
  }

  @Transactional
  public void recordAccess(User user, UUID entityId, String accessType, String outcome) {
    GlobalRecipientEntity entity =
        entityId == null ? null : entities.findById(entityId).orElse(null);
    recordAccess(user, entity, accessType, outcome);
  }

  private void recordAccess(
      User user, GlobalRecipientEntity entity, String accessType, String outcome) {
    RecipientIntelligenceAccessLog log = new RecipientIntelligenceAccessLog();
    log.setRecipientEntity(entity);
    log.setActorReference(user.getId());
    log.setAccessType(accessType);
    log.setOutcome(outcome);
    log.setAccessedAt(OffsetDateTime.now(ZoneOffset.UTC));
    accessLogs.save(log);
  }

  private Optional<IdentityMatch> authorizedMatch(
      User user, GlobalRecipientEntity entity) {
    if (user.isAdmin()) {
      return Optional.of(
          new IdentityMatch(
              entity,
              com.guardpay.intelligence.domain.IdentifierMatchLevel.EXACT_UNVERIFIED_MATCH,
              entity.getIdentityConfidence(),
              null));
    }
    if (relationships
        .findByOwnerReferenceAndRecipientEntityId(user.getId(), entity.getId())
        .isPresent()) {
      return Optional.of(
          new IdentityMatch(
              entity,
              com.guardpay.intelligence.domain.IdentifierMatchLevel.EXACT_UNVERIFIED_MATCH,
              entity.getIdentityConfidence(),
              null));
    }
    for (Recipient recipient : recipients.findByUserId(user.getId())) {
      Optional<IdentityMatch> match = identity.findForRecipient(recipient);
      if (match.isPresent() && entity.getId().equals(match.get().entity().getId())) {
        return match;
      }
    }
    return Optional.empty();
  }

  private void requireConfigured() {
    if (!properties.isEnabled() || !identity.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Recipient intelligence is not configured");
    }
  }

  private static Pageable pageable(int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    return PageRequest.of(safePage, safeSize);
  }

  private static AdminEvidenceDto toEvidenceDto(RecipientFraudEvidence row) {
    return new AdminEvidenceDto(
        row.getEvidenceType().name(),
        row.getSource().name(),
        row.getVerificationStatus().name(),
        row.getConfidence(),
        row.getReportCount(),
        row.getObservedAt(),
        row.getLastObservedAt(),
        row.getRevokedAt() != null);
  }

  private static AdminReportDto toReportDto(FraudIntelligenceReport report) {
    return new AdminReportDto(
        report.getId(),
        report.getReportStatus().name(),
        report.getCreatedAt(),
        report.getIncidentAt(),
        report.getRelatedTransactionId());
  }
}
