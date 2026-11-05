package com.guardpay.alert;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the notification outbox. Runs on a short fixed delay so CRITICAL
 * alerts leave the system within a couple of seconds without blocking the
 * payment request. Failed deliveries are recorded; this prototype does not
 * attempt unbounded retries.
 */
@Component
public class AlertNotificationDispatcher {

  private static final Logger log =
      LoggerFactory.getLogger(AlertNotificationDispatcher.class);

  private final NotificationOutboxRepository outbox;
  private final AlertRepository alerts;
  private final EmailAlertSender emailSender;

  public AlertNotificationDispatcher(
      NotificationOutboxRepository outbox,
      AlertRepository alerts,
      EmailAlertSender emailSender) {
    this.outbox = outbox;
    this.alerts = alerts;
    this.emailSender = emailSender;
  }

  @Scheduled(fixedDelayString = "${guardpay.alerts.dispatch-interval-ms:2000}")
  public void dispatchPending() {
    for (NotificationOutbox row :
        outbox.findTop50ByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING)) {
      deliver(row);
    }
  }

  private void deliver(NotificationOutbox row) {
    row.setAttempts(row.getAttempts() + 1);
    try {
      switch (row.getChannel()) {
        case IN_APP -> markSent(row);
        case EMAIL -> deliverEmail(row);
        case SMS -> markSkipped(row, "SMS provider is not configured in this prototype");
      }
    } catch (RuntimeException failure) {
      row.setStatus(NotificationStatus.FAILED);
      row.setLastError(truncate(failure.getMessage()));
      outbox.save(row);
      log.warn("Alert delivery failed (channel={}): {}", row.getChannel(), failure.getMessage());
    }
  }

  private void deliverEmail(NotificationOutbox row) {
    if (!emailSender.isConfigured()) {
      markSkipped(row, "Email provider is not configured");
      return;
    }
    Alert alert =
        alerts
            .findWithUserById(row.getAlert().getId())
            .orElse(null);
    if (alert == null) {
      markSkipped(row, "Alert no longer exists");
      return;
    }
    emailSender.send(alert, alert.getUser().getEmail());
    markSent(row);
  }

  private void markSent(NotificationOutbox row) {
    row.setStatus(NotificationStatus.SENT);
    row.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
    row.setLastError(null);
    outbox.save(row);
  }

  private void markSkipped(NotificationOutbox row, String reason) {
    row.setStatus(NotificationStatus.SKIPPED);
    row.setLastError(truncate(reason));
    outbox.save(row);
  }

  private static String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 512 ? value : value.substring(0, 512);
  }
}
