package com.guardpay.feedback;

import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User-facing feedback lifecycle; admin review lives under /api/admin/fraud-feedback. */
@RestController
@RequestMapping("/api/fraud-feedback")
public class FraudFeedbackController {

  private final FraudFeedbackService feedback;
  private final DemoUserService demoUser;

  public FraudFeedbackController(FraudFeedbackService feedback, DemoUserService demoUser) {
    this.feedback = feedback;
    this.demoUser = demoUser;
  }

  @PostMapping("/transactions/{transactionId}")
  public ResponseEntity<FraudFeedbackDto> submit(
      @PathVariable("transactionId") UUID transactionId,
      @RequestBody SubmitFeedbackRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    User user = demoUser.currentUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(feedback.submit(user, transactionId, request, idempotencyKey));
  }

  @GetMapping("/transactions/{transactionId}")
  public ResponseEntity<FraudFeedbackDto> forTransaction(
      @PathVariable("transactionId") UUID transactionId) {
    return feedback
        .forTransaction(demoUser.currentUser(), transactionId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/mine")
  public Page<FraudFeedbackDto> mine(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return feedback.mine(demoUser.currentUser(), page, size);
  }

  @GetMapping("/summary")
  public FeedbackSummaryDto summary() {
    return feedback.summary(demoUser.currentUser());
  }

  @GetMapping("/{feedbackId}")
  public FeedbackDetailDto detail(@PathVariable("feedbackId") UUID feedbackId) {
    return feedback.detail(demoUser.currentUser(), feedbackId);
  }
}
