package com.guardpay.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.guardpay.payment.PaymentVerification;
import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.payment.VerificationStatus;
import com.guardpay.recipient.Recipient;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RiskLevel;
import com.guardpay.transaction.dto.TransactionDetailDto;
import com.guardpay.transaction.dto.TransactionPageDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** History reads: owner scoping, latest decision, findings, verification outcome. */
@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceTest {

  @Mock private TransactionRepository transactions;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private PaymentVerificationRepository verifications;
  @Mock private DemoUserService demoUser;

  private TransactionQueryService service;
  private User owner;

  @BeforeEach
  void setUp() {
    service = new TransactionQueryService(transactions, assessments, verifications, demoUser);
    owner = new User();
    ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
    lenient().when(demoUser.currentUser()).thenReturn(owner);
  }

  private Transaction transaction(UUID id) {
    Transaction transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", id);
    transaction.setUser(owner);
    transaction.setAmount(new BigDecimal("45.00"));
    transaction.setCurrency("USD");
    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setTransactionTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
    Recipient recipient = new Recipient();
    recipient.setUser(owner);
    recipient.setName("Known Co");
    transaction.setRecipient(recipient);
    return transaction;
  }

  private RiskAssessment assessment(Transaction transaction) {
    RiskAssessment assessment = new RiskAssessment();
    assessment.setTransaction(transaction);
    assessment.setScore(60);
    assessment.setRiskLevel(RiskLevel.MEDIUM);
    assessment.setRecommendedAction(RecommendedAction.VERIFY);
    assessment.setExplanation("Some signals need confirmation.");
    return assessment;
  }

  @Test
  void listIsScopedToTheOwnerAndCarriesTheLatestDecision() {
    UUID id = UUID.randomUUID();
    Transaction transaction = transaction(id);
    when(transactions.findByUserId(eq(owner.getId()), any()))
        .thenReturn(new PageImpl<>(List.of(transaction)));
    RiskAssessment risk = assessment(transaction);
    when(assessments.findByTransactionIdInOrderByCreatedAtDesc(List.of(id)))
        .thenReturn(List.of(risk));

    TransactionPageDto page = service.list(0, 20);

    assertEquals(1, page.items().size());
    assertEquals(60, page.items().get(0).riskScore());
    assertEquals(RiskLevel.MEDIUM, page.items().get(0).riskLevel());
    assertEquals(RecommendedAction.VERIFY, page.items().get(0).recommendedAction());
    assertEquals("Known Co", page.items().get(0).recipientName());
  }

  @Test
  void detailIncludesParsedFindingsAndVerificationStatus() {
    UUID id = UUID.randomUUID();
    Transaction transaction = transaction(id);
    RiskAssessment risk = assessment(transaction);
    risk.setFindings(
        "[{\"code\":\"NEW_RECIPIENT\",\"title\":\"New recipient\","
            + "\"description\":\"First payment to this recipient.\",\"points\":20,"
            + "\"triggered\":true}]");
    PaymentVerification verification = new PaymentVerification();
    verification.setStatus(VerificationStatus.VERIFIED);
    when(transactions.findById(id)).thenReturn(Optional.of(transaction));
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction)).thenReturn(List.of(risk));
    when(verifications.findByTransactionId(id)).thenReturn(Optional.of(verification));

    TransactionDetailDto detail = service.detail(id);

    assertEquals(1, detail.assessment().findings().size());
    assertEquals("NEW_RECIPIENT", detail.assessment().findings().get(0).code());
    assertEquals(VerificationStatus.VERIFIED, detail.verificationStatus());
    assertEquals("Known Co", detail.recipientName());
  }

  @Test
  void unknownTransactionIsNotFound() {
    UUID id = UUID.randomUUID();
    when(transactions.findById(id)).thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.detail(id));
    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }
}
