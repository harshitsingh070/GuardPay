package com.guardpay.intelligence.repository;

import com.guardpay.intelligence.domain.FraudIntelligenceReport;
import com.guardpay.intelligence.domain.ReportStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Fraud report persistence and rate-limit counting. */
public interface FraudIntelligenceReportRepository
    extends JpaRepository<FraudIntelligenceReport, UUID> {

  Optional<FraudIntelligenceReport>
      findFirstByRecipientEntityIdAndReporterReferenceAndEvidenceFingerprint(
          UUID recipientEntityId, UUID reporterReference, String evidenceFingerprint);

  long countByReporterReferenceAndCreatedAtAfter(
      UUID reporterReference, OffsetDateTime after);

  long countByRecipientEntityIdAndReporterReferenceAndCreatedAtAfter(
      UUID recipientEntityId, UUID reporterReference, OffsetDateTime after);

  List<FraudIntelligenceReport> findByRecipientEntityIdOrderByCreatedAtDesc(
      UUID recipientEntityId);

  List<FraudIntelligenceReport> findByRecipientEntityIdAndReportStatusIn(
      UUID recipientEntityId, Collection<ReportStatus> statuses);

  Page<FraudIntelligenceReport> findByReporterReferenceOrderByCreatedAtDesc(
      UUID reporterReference, Pageable pageable);

  Optional<FraudIntelligenceReport> findByIdAndReporterReference(
      UUID id, UUID reporterReference);

  long countByReporterReference(UUID reporterReference);

  long countByReporterReferenceAndReportStatus(UUID reporterReference, ReportStatus status);

  @Query(
      """
      select count(distinct r.recipientEntity.id) from FraudIntelligenceReport r
      where r.reporterReference = :reporter
      """)
  long countDistinctRecipientsByReporter(@Param("reporter") UUID reporter);

  Page<FraudIntelligenceReport> findByReportStatusOrderByCreatedAtDesc(
      ReportStatus status, Pageable pageable);
}
