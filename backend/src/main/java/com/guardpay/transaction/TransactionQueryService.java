package com.guardpay.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.intelligence.dto.RecipientReputationDto;
import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.correlation.dto.CorrelationFindingDto;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.transaction.dto.RiskAssessmentViewDto;
import com.guardpay.transaction.dto.TransactionDetailDto;
import com.guardpay.transaction.dto.TransactionPageDto;
import com.guardpay.transaction.dto.TransactionSummaryDto;
import com.guardpay.user.DemoUserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only transaction history for the authenticated account.
 *
 * <p>The account is resolved server-side; callers cannot choose whose
 * transactions they read.
 */
@Service
public class TransactionQueryService {

  static final int MAX_PAGE_SIZE = 100;

  private static final ObjectMapper JSON = new ObjectMapper();

  private final TransactionRepository transactions;
  private final RiskAssessmentRepository assessments;
  private final PaymentVerificationRepository verifications;
  private final DemoUserService demoUser;

  public TransactionQueryService(
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PaymentVerificationRepository verifications,
      DemoUserService demoUser) {
    this.transactions = transactions;
    this.assessments = assessments;
    this.verifications = verifications;
    this.demoUser = demoUser;
  }

  @Transactional(readOnly = true)
  public TransactionPageDto list(int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    Pageable pageable =
        PageRequest.of(
            safePage, safeSize, Sort.by(Sort.Direction.DESC, "transactionTimestamp"));
    Page<Transaction> result = transactions.findByUserId(demoUser.currentUser().getId(), pageable);
    List<Transaction> rows = result.getContent();
    List<UUID> ids = rows.stream().map(Transaction::getId).toList();
    Map<UUID, RiskAssessment> latest = new HashMap<>();
    if (!ids.isEmpty()) {
      for (RiskAssessment assessment :
          assessments.findByTransactionIdInOrderByCreatedAtDesc(ids)) {
        latest.putIfAbsent(assessment.getTransaction().getId(), assessment);
      }
    }
    List<TransactionSummaryDto> items =
        rows.stream().map(row -> toSummary(row, latest.get(row.getId()))).toList();
    return new TransactionPageDto(
        items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public TransactionDetailDto detail(UUID transactionId) {
    Transaction transaction =
        transactions
            .findByIdAndUserId(transactionId, demoUser.currentUser().getId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction"));
    return toDetail(transaction);
  }

  private TransactionDetailDto toDetail(Transaction transaction) {
    RiskAssessment latest =
        assessments.findByTransactionOrderByCreatedAtDesc(transaction).stream()
            .findFirst()
            .orElse(null);
    RiskAssessmentViewDto assessment =
        latest == null
            ? null
            : new RiskAssessmentViewDto(
                latest.getScore(),
                latest.getRiskLevel(),
                latest.getRecommendedAction(),
                latest.getExplanation(),
                parseFindings(latest.getFindings()),
                latest.getCreatedAt(),
                parseCorrelationFindings(latest.getCorrelationFindings()),
                latest.getCorrelationStatus(),
                latest.getCorrelationContribution(),
                parseRecipientReputation(latest.getRecipientReputation()),
                latest.getRecipientReputationPoints());
    return new TransactionDetailDto(
        transaction.getId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        transaction.getStatus(),
        transaction.getDescription(),
        transaction.getTransactionTimestamp(),
        transaction.getCreatedAt(),
        nameOf(transaction.getRecipient() == null ? null : transaction.getRecipient().getName()),
        nameOf(transaction.getMerchant() == null ? null : transaction.getMerchant().getName()),
        transaction.getPaypalOrderId(),
        transaction.getPaypalCaptureId(),
        transaction.getPaypalTransactionId(),
        assessment,
        verifications
            .findByTransactionId(transaction.getId())
            .map(verification -> verification.getStatus())
            .orElse(null));
  }

  private static TransactionSummaryDto toSummary(
      Transaction transaction, RiskAssessment assessment) {
    return new TransactionSummaryDto(
        transaction.getId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        transaction.getStatus(),
        transaction.getTransactionTimestamp(),
        transaction.getDescription(),
        nameOf(transaction.getRecipient() == null ? null : transaction.getRecipient().getName()),
        nameOf(transaction.getMerchant() == null ? null : transaction.getMerchant().getName()),
        assessment == null ? null : assessment.getScore(),
        assessment == null ? null : assessment.getRiskLevel(),
        assessment == null ? null : assessment.getRecommendedAction());
  }

  private static String nameOf(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static List<RiskFindingDto> parseFindings(String findings) {
    if (findings == null || findings.isBlank()) {
      return List.of();
    }
    try {
      return JSON.readValue(findings, new TypeReference<List<RiskFindingDto>>() {});
    } catch (JsonProcessingException unreadable) {
      return List.of();
    }
  }

  private static List<CorrelationFindingDto> parseCorrelationFindings(String findings) {
    if (findings == null || findings.isBlank()) {
      return List.of();
    }
    try {
      return JSON.readValue(findings, new TypeReference<List<CorrelationFindingDto>>() {});
    } catch (JsonProcessingException unreadable) {
      return List.of();
    }
  }

  private static RecipientReputationDto parseRecipientReputation(String reputation) {
    if (reputation == null || reputation.isBlank()) {
      return null;
    }
    try {
      return JSON.readValue(reputation, RecipientReputationDto.class);
    } catch (JsonProcessingException unreadable) {
      return null;
    }
  }
}

