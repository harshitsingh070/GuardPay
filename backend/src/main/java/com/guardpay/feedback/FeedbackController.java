package com.guardpay.feedback;

import com.guardpay.intelligence.web.AdminGuard;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit feedback labels plus admin-only evaluation metrics/export. */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

  private final FraudFeedbackService feedback;
  private final DemoUserService demoUser;
  private final AdminGuard adminGuard;

  public FeedbackController(
      FraudFeedbackService feedback, DemoUserService demoUser, AdminGuard adminGuard) {
    this.feedback = feedback;
    this.demoUser = demoUser;
    this.adminGuard = adminGuard;
  }

  @PostMapping("/transactions/{transactionId}")
  public ResponseEntity<FraudFeedbackDto> submit(
      @PathVariable("transactionId") UUID transactionId,
      @RequestBody SubmitFeedbackRequest request) {
    User user = demoUser.currentUser();
    FraudFeedbackDto saved = feedback.submit(user, transactionId, request.label());
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  @GetMapping("/transactions/{transactionId}")
  public ResponseEntity<FraudFeedbackDto> forTransaction(
      @PathVariable("transactionId") UUID transactionId) {
    User user = demoUser.currentUser();
    return feedback
        .forTransaction(user, transactionId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/metrics")
  public FeedbackMetricsDto metrics() {
    User user = demoUser.currentUser();
    adminGuard.requireAdmin(user);
    return feedback.metrics();
  }

  @GetMapping("/export")
  public List<FraudFeedbackDto> export() {
    User user = demoUser.currentUser();
    adminGuard.requireAdmin(user);
    return feedback.export();
  }
}
