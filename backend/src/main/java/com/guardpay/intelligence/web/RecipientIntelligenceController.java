package com.guardpay.intelligence.web;

import com.guardpay.intelligence.dto.FraudReportResponse;
import com.guardpay.intelligence.dto.IntelligenceAggregateDto;
import com.guardpay.intelligence.dto.MyReportDto;
import com.guardpay.intelligence.dto.RecipientReputationSummaryDto;
import com.guardpay.intelligence.dto.SubmitFraudReportRequest;
import com.guardpay.intelligence.service.FraudReportService;
import com.guardpay.intelligence.service.RecipientIntelligenceQueryService;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User-facing recipient intelligence: report submission and own-report views. */
@RestController
@RequestMapping("/api/intelligence")
public class RecipientIntelligenceController {

  private final FraudReportService fraudReports;
  private final RecipientIntelligenceQueryService queries;
  private final DemoUserService demoUser;

  public RecipientIntelligenceController(
      FraudReportService fraudReports,
      RecipientIntelligenceQueryService queries,
      DemoUserService demoUser) {
    this.fraudReports = fraudReports;
    this.queries = queries;
    this.demoUser = demoUser;
  }

  @PostMapping("/recipients/reports")
  public ResponseEntity<FraudReportResponse> submitReport(
      @Valid @RequestBody SubmitFraudReportRequest request) {
    User user = demoUser.currentUser();
    FraudReportResponse response = fraudReports.submit(user, request);
    queries.recordAccess(user, response.recipientReference(), "REPORT", "SUBMITTED");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/recipients/{recipientReference}/summary")
  public RecipientReputationSummaryDto summary(
      @PathVariable("recipientReference") UUID recipientReference) {
    return queries.summary(demoUser.currentUser(), recipientReference);
  }

  @GetMapping("/reports/{reportId}")
  public MyReportDto report(@PathVariable("reportId") UUID reportId) {
    return queries.myReport(demoUser.currentUser(), reportId);
  }

  @GetMapping("/reports")
  public Page<MyReportDto> reports(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return queries.myReports(demoUser.currentUser(), page, size);
  }

  @GetMapping("/summary")
  public IntelligenceAggregateDto summary() {
    return queries.aggregate(demoUser.currentUser());
  }
}
