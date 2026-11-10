package com.guardpay.intelligence.web;

import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.ReportStatus;
import com.guardpay.intelligence.domain.ReviewAction;
import com.guardpay.intelligence.dto.AdminRecipientDetailDto;
import com.guardpay.intelligence.dto.AdminReportDto;
import com.guardpay.intelligence.dto.AdminReviewRequest;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.service.RecipientIntelligenceQueryService;
import com.guardpay.intelligence.service.RecipientReputationService;
import com.guardpay.intelligence.service.RecipientReviewService;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only review console for global recipient reputation. */
@RestController
@RequestMapping("/api/admin/intelligence")
public class AdminRecipientIntelligenceController {

  private final RecipientIntelligenceQueryService queries;
  private final RecipientReviewService reviews;
  private final RecipientReputationService reputation;
  private final GlobalRecipientEntityRepository entities;
  private final DemoUserService demoUser;
  private final AdminGuard adminGuard;

  public AdminRecipientIntelligenceController(
      RecipientIntelligenceQueryService queries,
      RecipientReviewService reviews,
      RecipientReputationService reputation,
      GlobalRecipientEntityRepository entities,
      DemoUserService demoUser,
      AdminGuard adminGuard) {
    this.queries = queries;
    this.reviews = reviews;
    this.reputation = reputation;
    this.entities = entities;
    this.demoUser = demoUser;
    this.adminGuard = adminGuard;
  }

  @GetMapping("/recipients/reports")
  public Page<AdminReportDto> reports(
      @RequestParam(required = false) ReportStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    adminGuard.requireAdmin(demoUser.currentUser());
    return queries.adminReports(status, page, size);
  }

  @GetMapping("/recipients/{recipientReference}")
  public AdminRecipientDetailDto detail(
      @PathVariable("recipientReference") UUID recipientReference) {
    User admin = demoUser.currentUser();
    adminGuard.requireAdmin(admin);
    AdminRecipientDetailDto detail = queries.adminDetail(recipientReference);
    queries.recordAccess(admin, recipientReference, "ADMIN_DETAIL", "AUTHORIZED");
    return detail;
  }

  @PostMapping("/recipients/{recipientReference}/review")
  public AdminRecipientDetailDto review(
      @PathVariable("recipientReference") UUID recipientReference,
      @Valid @RequestBody AdminReviewRequest request) {
    User admin = demoUser.currentUser();
    adminGuard.requireAdmin(admin);
    reviews.review(recipientReference, admin, request.action(), request.note());
    queries.recordAccess(admin, recipientReference, "ADMIN_REVIEW", request.action().name());
    return queries.adminDetail(recipientReference);
  }

  @PostMapping("/recipients/{recipientReference}/clear")
  public AdminRecipientDetailDto clear(
      @PathVariable("recipientReference") UUID recipientReference,
      @RequestParam(required = false) String note) {
    User admin = demoUser.currentUser();
    adminGuard.requireAdmin(admin);
    reviews.review(recipientReference, admin, ReviewAction.CLEAR, note);
    queries.recordAccess(admin, recipientReference, "ADMIN_CLEAR", "CLEARED");
    return queries.adminDetail(recipientReference);
  }

  @PostMapping("/recipients/{recipientReference}/recalculate")
  public AdminRecipientDetailDto recalculate(
      @PathVariable("recipientReference") UUID recipientReference) {
    User admin = demoUser.currentUser();
    adminGuard.requireAdmin(admin);
    GlobalRecipientEntity entity =
        entities
            .findById(recipientReference)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient"));
    reputation.recalculate(entity);
    queries.recordAccess(admin, recipientReference, "ADMIN_RECALCULATE", "OK");
    return queries.adminDetail(recipientReference);
  }
}
