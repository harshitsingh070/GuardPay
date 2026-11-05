package com.guardpay.dashboard;

import com.guardpay.alert.AlertRepository;
import com.guardpay.alert.AlertSeverity;
import com.guardpay.alert.NotificationOutbox;
import com.guardpay.alert.NotificationOutboxRepository;
import com.guardpay.alert.NotificationStatus;
import com.guardpay.dashboard.dto.DashboardSummaryDto;
import com.guardpay.dashboard.dto.DashboardSummaryDto.AlertStatsDto;
import com.guardpay.dashboard.dto.DashboardSummaryDto.AiDto;
import com.guardpay.dashboard.dto.DashboardSummaryDto.LatencyDto;
import com.guardpay.dashboard.dto.DashboardSummaryDto.VerificationStatsDto;
import com.guardpay.metrics.DecisionMetrics;
import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.payment.VerificationStatus;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.user.DemoUserService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the live dashboard from stored outcomes and in-memory latency. */
@Service
public class DashboardService {

  private final RiskAssessmentRepository assessments;
  private final PaymentVerificationRepository verifications;
  private final AlertRepository alerts;
  private final NotificationOutboxRepository outbox;
  private final DecisionMetrics metrics;
  private final DemoUserService demoUser;

  public DashboardService(
      RiskAssessmentRepository assessments,
      PaymentVerificationRepository verifications,
      AlertRepository alerts,
      NotificationOutboxRepository outbox,
      DecisionMetrics metrics,
      DemoUserService demoUser) {
    this.assessments = assessments;
    this.verifications = verifications;
    this.alerts = alerts;
    this.outbox = outbox;
    this.metrics = metrics;
    this.demoUser = demoUser;
  }

  @Transactional(readOnly = true)
  public DashboardSummaryDto summary() {
    UUID userId = demoUser.currentUser().getId();
    DecisionMetrics.Snapshot live = metrics.snapshot();

    long decisions = assessments.countForUser(userId);
    long allow = assessments.countForUserByAction(userId, RecommendedAction.ALLOW);
    long verify = assessments.countForUserByAction(userId, RecommendedAction.VERIFY);
    long stop = assessments.countForUserByAction(userId, RecommendedAction.STOP);
    BigDecimal blocked =
        assessments.sumAmountForUserByAction(userId, RecommendedAction.STOP);
    Double average = assessments.averageScoreForUser(userId);

    long pendingAlerts = outbox.countByAlertUserIdAndStatus(userId, NotificationStatus.PENDING);
    long sentAlerts = outbox.countByAlertUserIdAndStatus(userId, NotificationStatus.SENT);

    return new DashboardSummaryDto(
        decisions,
        allow,
        verify,
        stop,
        blocked == null ? BigDecimal.ZERO : blocked,
        average == null ? 0.0 : Math.round(average * 10.0) / 10.0,
        new LatencyDto(live.p50LatencyMs(), live.p95LatencyMs(), live.latencySamples()),
        new AiDto(
            live.aiRequests(),
            live.aiAvailable(),
            live.aiUnavailable(),
            round(live.aiAvailabilityPct())),
        new AlertStatsDto(
            alerts.countByUserId(userId),
            alerts.countByUserIdAndSeverity(userId, AlertSeverity.CRITICAL),
            pendingAlerts,
            sentAlerts,
            deliveryP95()),
        verificationStats(userId));
  }

  private VerificationStatsDto verificationStats(UUID userId) {
    long pending = verifications.countForUserByStatus(userId, VerificationStatus.PENDING);
    long verified = verifications.countForUserByStatus(userId, VerificationStatus.VERIFIED);
    long failed = verifications.countForUserByStatus(userId, VerificationStatus.FAILED);
    long expired = verifications.countForUserByStatus(userId, VerificationStatus.EXPIRED);
    long completed = verified + failed + expired;
    double successRate = completed == 0 ? 0.0 : (verified * 100.0) / completed;
    return new VerificationStatsDto(
        pending, verified, failed, expired, round(successRate));
  }

  /** p95 latency from outbox creation to successful delivery. */
  private Long deliveryP95() {
    List<NotificationOutbox> sent =
        outbox.findTop200ByStatusOrderByCreatedAtDesc(NotificationStatus.SENT);
    long[] durations =
        sent.stream()
            .filter(row -> row.getSentAt() != null && row.getCreatedAt() != null)
            .mapToLong(
                row ->
                    Math.max(
                        0L,
                        Duration.between(row.getCreatedAt(), row.getSentAt()).toMillis()))
            .toArray();
    if (durations.length == 0) {
      return null;
    }
    Arrays.sort(durations);
    int index = (int) Math.ceil(0.95 * durations.length) - 1;
    return durations[Math.max(0, Math.min(index, durations.length - 1))];
  }

  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
