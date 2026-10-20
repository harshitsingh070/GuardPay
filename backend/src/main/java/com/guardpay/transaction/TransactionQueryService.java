package com.guardpay.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.transaction.dto.RiskAssessmentViewDto;
import com.guardpay.transaction.dto.TransactionDetailDto;
import com.guardpay.transaction.dto.TransactionPageDto;
import com.guardpay.transaction.dto.TransactionSummaryDto;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
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
 * Read-only transaction history.
 *
 * <p>Ownership is enforced here: a caller may only ever see their own
 * transactions. With no authentication yet the owner is a query parameter, and
 * an omitted owner resolves to the documented demo user — a real user id that
 * has no transactions simply gets an empty page, and a foreign transaction id
 * is reported as not found rather than exposed.
 */
@Service
public class TransactionQueryService {

  static final int MAX_PAGE_SIZE = 100;

  private static final ObjectMapper JSON = new ObjectMapper();

  private final TransactionRepository transactions;
  private final RiskAssessmentRepository assessments;
  private final PaymentVerificationRepository verifications;
  private final UserRepository users;

  public TransactionQueryService(
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PaymentVerificationRepository verifications,
      UserRepository users) {
    this.transactions = transactions;
    this.assessments = assessments;
    this.verifications = verifications;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public TransactionPageDto list(UUID userId, int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    User owner = resolveOwner(userId);
    if (owner == null) {
      return new TransactionPageDto(List.of(), safePage, safeSize, 0, 0);
    }
    Pageable pageable =
        PageRequest.of(
            safePage, safeSize, Sort.by(Sort.Direction.DESC, "transactionTimestamp"));
    Page<Transaction> result = transactions.findByUserId(owner.getId(), pageable);
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
  public TransactionDetailDto detail(UUID transactionId, UUID userId) {
    User owner = resolveOwner(userId);
    Transaction transaction =
        transactions
            .findById(transactionId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction"));
    if (owner == null
        || transaction.getUser() == null
        || !owner.getId().equals(transaction.getUser().getId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction");
    }
    return toDetail(transaction);
  }

  private User resolveOwner(UUID userId) {
    if (userId != null) {
      return users
          .findById(userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown user"));
    }
    return users.findByExternalUserId(PayPalOrderService.DEMO_USER_EXTERNAL_ID).orElse(null);
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
                latest.getCreatedAt());
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
}
