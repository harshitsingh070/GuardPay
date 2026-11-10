package com.guardpay.intelligence.service;

import com.guardpay.intelligence.config.RecipientIntelligenceProperties;
import com.guardpay.intelligence.domain.EvidenceSource;
import com.guardpay.intelligence.domain.EvidenceType;
import com.guardpay.intelligence.domain.EvidenceVerificationStatus;
import com.guardpay.intelligence.domain.FraudIntelligenceReport;
import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.IdentifierType;
import com.guardpay.intelligence.domain.IdentifierVerificationStatus;
import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.RecipientRelationship;
import com.guardpay.intelligence.domain.RelationshipConfidence;
import com.guardpay.intelligence.domain.RelationshipType;
import com.guardpay.intelligence.dto.FraudReportResponse;
import com.guardpay.intelligence.dto.SubmitFraudReportRequest;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.repository.FraudIntelligenceReportRepository;
import com.guardpay.intelligence.repository.RecipientRelationshipRepository;
import com.guardpay.intelligence.service.RecipientIdentityService.IdentityMatch;
import com.guardpay.recipient.Recipient;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ingests fraud reports and turns them into deduplicated, low-weight evidence.
 *
 * <p>Reports are rate limited per reporter and per recipient, require either
 * an owned transaction or an explicit identifier, and never store the raw
 * identifier. A report alone can raise suspicion but cannot confirm fraud.
 */
@Service
public class FraudReportService {

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  private static final String DEFAULT_DESCRIPTION =
      "Recipient reported for suspected fraud.";

  private final RecipientIntelligenceProperties properties;
  private final RecipientIdentityService identity;
  private final RecipientReputationService reputation;
  private final FraudIntelligenceReportRepository reports;
  private final RecipientRelationshipRepository relationships;
  private final TransactionRepository transactions;

  public FraudReportService(
      RecipientIntelligenceProperties properties,
      RecipientIdentityService identity,
      RecipientReputationService reputation,
      FraudIntelligenceReportRepository reports,
      RecipientRelationshipRepository relationships,
      TransactionRepository transactions) {
    this.properties = properties;
    this.identity = identity;
    this.reputation = reputation;
    this.reports = reports;
    this.relationships = relationships;
    this.transactions = transactions;
  }

  @Transactional
  public FraudReportResponse submit(User reporter, SubmitFraudReportRequest request) {
    if (reporter == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (!properties.isEnabled() || !identity.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Recipient intelligence is not configured");
    }
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report body is required");
    }

    ResolvedTarget target = resolveTarget(reporter, request);
    GlobalRecipientEntity entity = target.entity;
    UUID reporterId = reporter.getId();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    enforceRateLimits(reporterId, entity.getId(), now);

    String reporterGroup = reporterGroupKey(entity.getId(), reporterId);
    String reportFingerprint =
        RecipientIdentifierHasher.sha256Hex(
            entity.getId() + "|USER_REPORT|" + DAY.format(now));
    Optional<FraudIntelligenceReport> duplicate =
        reports.findFirstByRecipientEntityIdAndReporterReferenceAndEvidenceFingerprint(
            entity.getId(), reporterId, reportFingerprint);
    if (duplicate.isPresent()) {
      FraudIntelligenceReport existing = duplicate.get();
      return new FraudReportResponse(
          existing.getId(), entity.getId(), existing.getReportStatus().name(), existing.getCreatedAt());
    }

    String description = sanitize(request.description());
    FraudIntelligenceReport report = new FraudIntelligenceReport();
    report.setRecipientEntity(entity);
    report.setReporterReference(reporterId);
    report.setReportStatus(com.guardpay.intelligence.domain.ReportStatus.SUBMITTED);
    report.setDescription(description);
    report.setEvidenceFingerprint(reportFingerprint);
    report.setRelatedTransactionId(target.relatedTransactionId);
    report.setIncidentAt(request.incidentAt());
    reports.save(report);

    String evidenceFingerprint =
        RecipientIdentifierHasher.sha256Hex(
            entity.getId() + "|USER_REPORT|RECIPIENT_REPORTED|" + reporterGroup);
    reputation.recordEvidence(
        entity,
        EvidenceType.RECIPIENT_REPORTED,
        EvidenceSource.USER_REPORT,
        0.5,
        EvidenceVerificationStatus.UNVERIFIED,
        evidenceFingerprint,
        reporterGroup,
        target.relatedTransactionId,
        description,
        request.incidentAt() == null ? now : request.incidentAt(),
        report);
    upsertRelationship(reporterId, entity, now);
    reputation.recalculate(entity);

    return new FraudReportResponse(
        report.getId(), entity.getId(), report.getReportStatus().name(), report.getCreatedAt());
  }

  private ResolvedTarget resolveTarget(User reporter, SubmitFraudReportRequest request) {
    UUID relatedTransactionId = request.relatedTransactionId();
    if (relatedTransactionId != null) {
      Transaction transaction =
          transactions
              .findByIdAndUserId(relatedTransactionId, reporter.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, "Unknown transaction"));
      Recipient recipient = transaction.getRecipient();
      if (recipient == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Payment has no recipient to report");
      }
      IdentityMatch match =
          identity
              .resolveForRecipient(recipient)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.SERVICE_UNAVAILABLE,
                          "Recipient identity is unavailable"));
      verifyReference(request.recipientReference(), match.entity().getId());
      return new ResolvedTarget(match.entity(), relatedTransactionId);
    }

    IdentifierType type = request.identifierType();
    String value = request.identifierValue();
    if (type == null || value == null || value.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Provide relatedTransactionId or identifierType with identifierValue");
    }
    if (type == IdentifierType.PHONE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Phone identifiers are not supported");
    }
    IdentityMatch match;
    try {
      match =
          identity
              .resolveIdentifier(
                  type, value, IdentifierVerificationStatus.UNVERIFIED, IdentityConfidence.MEDIUM)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.SERVICE_UNAVAILABLE,
                          "Recipient identity is unavailable"));
    } catch (IllegalArgumentException invalid) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Identifier value is not valid", invalid);
    }
    verifyReference(request.recipientReference(), match.entity().getId());
    return new ResolvedTarget(match.entity(), null);
  }

  private static void verifyReference(UUID supplied, UUID resolved) {
    if (supplied != null && !supplied.equals(resolved)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "recipientReference does not match the supplied payment");
    }
  }

  private void enforceRateLimits(UUID reporterId, UUID entityId, OffsetDateTime now) {
    long lastHour =
        reports.countByReporterReferenceAndCreatedAtAfter(reporterId, now.minusHours(1));
    if (lastHour >= Math.max(1, properties.getReportRateLimitPerHour())) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Too many reports submitted recently");
    }
    long lastDay =
        reports.countByRecipientEntityIdAndReporterReferenceAndCreatedAtAfter(
            entityId, reporterId, now.minusDays(1));
    if (lastDay >= Math.max(1, properties.getReportRateLimitPerRecipientPerDay())) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "You have already reported this recipient today");
    }
  }

  private void upsertRelationship(
      UUID ownerReference, GlobalRecipientEntity entity, OffsetDateTime now) {
    RecipientRelationship relationship =
        relationships
            .findByOwnerReferenceAndRecipientEntityId(ownerReference, entity.getId())
            .orElseGet(
                () -> {
                  RecipientRelationship created = new RecipientRelationship();
                  created.setOwnerReference(ownerReference);
                  created.setRecipientEntity(entity);
                  created.setRelationshipType(RelationshipType.REPORTED);
                  created.setConfidence(RelationshipConfidence.MEDIUM);
                  created.setFirstSeenAt(now);
                  created.setInteractionCount(0);
                  return created;
                });
    relationship.setLastSeenAt(now);
    relationship.setInteractionCount(relationship.getInteractionCount() + 1);
    relationships.save(relationship);
  }

  private static String reporterGroupKey(UUID entityId, UUID reporterId) {
    return RecipientIdentifierHasher.sha256Hex(entityId + "|" + reporterId).substring(0, 32);
  }

  private String sanitize(String description) {
    if (description == null || description.isBlank()) {
      return DEFAULT_DESCRIPTION;
    }
    String cleaned = description.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    int max = Math.max(1, properties.getMaxDescriptionLength());
    if (cleaned.length() > max) {
      cleaned = cleaned.substring(0, max);
    }
    return cleaned.isBlank() ? DEFAULT_DESCRIPTION : cleaned;
  }

  private record ResolvedTarget(GlobalRecipientEntity entity, UUID relatedTransactionId) {}
}
