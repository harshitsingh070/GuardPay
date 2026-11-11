package com.guardpay.feedback;

import com.guardpay.intelligence.web.AdminGuard;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only feedback review, metrics, and export. */
@RestController
@RequestMapping("/api/admin/fraud-feedback")
public class AdminFraudFeedbackController {

  private final FraudFeedbackService feedback;
  private final FraudFeedbackReviewService reviews;
  private final DemoUserService demoUser;
  private final AdminGuard adminGuard;

  public AdminFraudFeedbackController(
      FraudFeedbackService feedback,
      FraudFeedbackReviewService reviews,
      DemoUserService demoUser,
      AdminGuard adminGuard) {
    this.feedback = feedback;
    this.reviews = reviews;
    this.demoUser = demoUser;
    this.adminGuard = adminGuard;
  }

  @GetMapping
  public Page<FraudFeedbackDto> list(
      @RequestParam(required = false) FeedbackStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    adminGuard.requireAdmin(demoUser.currentUser());
    return feedback.adminList(status, page, size);
  }

  @GetMapping("/summary")
  public FeedbackSummaryDto summary() {
    adminGuard.requireAdmin(demoUser.currentUser());
    return feedback.adminSummary();
  }

  @GetMapping("/metrics")
  public FeedbackMetricsDto metrics() {
    adminGuard.requireAdmin(demoUser.currentUser());
    return feedback.metrics();
  }

  @GetMapping("/export")
  public List<FraudFeedbackDto> export() {
    adminGuard.requireAdmin(demoUser.currentUser());
    return feedback.export();
  }

  @GetMapping("/{feedbackId}")
  public FeedbackDetailDto detail(@PathVariable("feedbackId") UUID feedbackId) {
    adminGuard.requireAdmin(demoUser.currentUser());
    return feedback.adminDetail(feedbackId);
  }

  @PostMapping("/{feedbackId}/review")
  public FeedbackDetailDto review(
      @PathVariable("feedbackId") UUID feedbackId,
      @RequestBody FeedbackReviewRequest request) {
    User admin = demoUser.currentUser();
    adminGuard.requireAdmin(admin);
    reviews.review(feedbackId, admin, request);
    return feedback.adminDetail(feedbackId);
  }
}
