package com.guardpay.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.alert.dto.AlertDto;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.DemoUserService;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Creates alerts in the same transaction as the decision and broadcasts them
 * only after commit. Delivery state is persisted in the notification outbox.
 */
@Service
public class AlertService {

  private static final Logger log = LoggerFactory.getLogger(AlertService.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  static final BigDecimal NEW_DEVICE_ALERT_AMOUNT = new BigDecimal("500.00");
  static final BigDecimal LARGE_CAPTURE_AMOUNT = new BigDecimal("1000.00");
  static final int HIGH_RISK_SCORE = 70;

  private final AlertRepository alerts;
  private final NotificationOutboxRepository outbox;
  private final AlertStreamService stream;
  private final DemoUserService demoUser;

  public AlertService(
      AlertRepository alerts,
      NotificationOutboxRepository outbox,
      AlertStreamService stream,
      DemoUserService demoUser) {
    this.alerts = alerts;
    this.outbox = outbox;
    this.stream = stream;
    this.demoUser = demoUser;
  }

  /**
   * Alert triggers (avoid fatigue): any STOP, score >= 70, or a new device
   * combined with a >= $500 payment.
   */
  @Transactional
  public void recordPaymentDecision(
      Transaction transaction, AutomaticEvaluationResponseDto risk) {
    if (transaction == null || risk == null || transaction.getId() == null) {
      return;
    }
    boolean stopped = risk.recommendedAction() == RecommendedAction.STOP;
    boolean highScore = risk.score() >= HIGH_RISK_SCORE;
    boolean newDeviceHighValue =
        signalDetected(risk, PaymentSignalType.NEW_DEVICE)
            && transaction.getAmount() != null
            && transaction.getAmount().compareTo(NEW_DEVICE_ALERT_AMOUNT) >= 0;
    if (!stopped && !highScore && !newDeviceHighValue) {
      return;
    }
    if (alerts.existsByTransactionIdAndType(transaction.getId(), AlertType.PAYMENT_RISK)) {
      return;
    }

    AlertSeverity severity =
        stopped ? AlertSeverity.CRITICAL : highScore ? AlertSeverity.HIGH : AlertSeverity.WARNING;
    String title =
        stopped
            ? "Payment blocked"
            : highScore ? "High-risk payment needs verification" : "Payment from a new device";
    String message =
        buildMessage(transaction, risk, stopped, newDeviceHighValue);

    saveAlert(
        transaction,
        recipientName(transaction),
        AlertType.PAYMENT_RISK,
        severity,
        title,
        message,
        risk.score(),
        risk.recommendedAction(),
        triggeredCodes(risk));
  }

  /** Alert trigger: any captured payment at or above $1,000. */
  @Transactional
  public void recordLargeCapture(Transaction transaction) {
    if (transaction == null
        || transaction.getId() == null
        || transaction.getStatus() != TransactionStatus.COMPLETED
        || transaction.getAmount() == null
        || transaction.getAmount().compareTo(LARGE_CAPTURE_AMOUNT) < 0) {
      return;
    }
    if (alerts.existsByTransactionIdAndType(transaction.getId(), AlertType.LARGE_CAPTURE)) {
      return;
    }
    saveAlert(
        transaction,
        recipientName(transaction),
        AlertType.LARGE_CAPTURE,
        AlertSeverity.WARNING,
        "Large payment captured",
        "A payment of "
            + transaction.getAmount().toPlainString()
            + " "
            + transaction.getCurrency()
            + " was captured. Review it if you did not expect this charge.",
        null,
        RecommendedAction.ALLOW,
        List.of());
  }

  @Transactional(readOnly = true)
  public List<AlertDto> recent(int limit) {
    int safeLimit = Math.min(Math.max(1, limit), 20);
    return alerts.findTop20ByUserIdOrderByCreatedAtDesc(demoUser.currentUser().getId()).stream()
        .limit(safeLimit)
        .map(AlertService::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public long count() {
    return alerts.countByUserId(demoUser.currentUser().getId());
  }

  private void saveAlert(
      Transaction transaction,
      String recipientName,
      AlertType type,
      AlertSeverity severity,
      String title,
      String message,
      Integer score,
      RecommendedAction action,
      List<String> signals) {
    Alert alert = new Alert();
    alert.setUser(transaction.getUser());
    alert.setTransaction(transaction);
    alert.setType(type);
    alert.setSeverity(severity);
    alert.setTitle(title);
    alert.setMessage(message);
    alert.setScore(score);
    alert.setRecommendedAction(action);
    alert.setAmount(transaction.getAmount());
    alert.setCurrency(transaction.getCurrency());
    alert.setRecipientName(recipientName);
    alert.setSignals(writeSignals(signals));
    Alert saved = alerts.save(alert);

    enqueue(saved, NotificationChannel.IN_APP);
    if (severity == AlertSeverity.CRITICAL) {
      enqueue(saved, NotificationChannel.EMAIL);
      enqueue(saved, NotificationChannel.SMS);
    }
    publishAfterCommit(saved);
  }

  private void enqueue(Alert alert, NotificationChannel channel) {
    if (outbox.existsByAlertIdAndChannel(alert.getId(), channel)) {
      return;
    }
    NotificationOutbox row = new NotificationOutbox();
    row.setAlert(alert);
    row.setChannel(channel);
    row.setStatus(NotificationStatus.PENDING);
    outbox.save(row);
  }

  private void publishAfterCommit(Alert alert) {
    AlertDto dto = toDto(alert);
    java.util.UUID ownerId = alert.getUser() == null ? null : alert.getUser().getId();
    Runnable publish = () -> stream.publish(ownerId, dto);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publish.run();
            }
          });
    } else {
      publish.run();
    }
    log.info("Alert recorded (type={}, severity={})", alert.getType(), alert.getSeverity());
  }

  private static String buildMessage(
      Transaction transaction,
      AutomaticEvaluationResponseDto risk,
      boolean stopped,
      boolean newDeviceHighValue) {
    String amount =
        transaction.getAmount() == null
            ? "this payment"
            : transaction.getAmount().toPlainString() + " " + transaction.getCurrency();
    String reasons = String.join(", ", triggeredCodes(risk));
    if (stopped) {
      return "GuardPay blocked "
          + amount
          + " before a PayPal order was created. Signals: "
          + (reasons.isBlank() ? "risk threshold reached" : reasons)
          + ".";
    }
    if (newDeviceHighValue) {
      return "A "
          + amount
          + " payment was started from a new device. GuardPay asked for extra verification.";
    }
    return "GuardPay scored "
          + amount
          + " at "
          + risk.score()
          + "/100 and asked for verification. Signals: "
          + (reasons.isBlank() ? "elevated risk" : reasons)
          + ".";
  }

  private static boolean signalDetected(
      AutomaticEvaluationResponseDto risk, PaymentSignalType type) {
    return risk.automaticSignals() != null
        && risk.automaticSignals().stream()
            .anyMatch(signal -> signal.type() == type && signal.detected());
  }

  private static List<String> triggeredCodes(AutomaticEvaluationResponseDto risk) {
    if (risk.findings() == null) {
      return List.of();
    }
    return risk.findings().stream()
        .filter(RiskFindingDto::triggered)
        .map(RiskFindingDto::code)
        .toList();
  }

  private static String recipientName(Transaction transaction) {
    return transaction.getRecipient() == null ? null : transaction.getRecipient().getName();
  }

  private static String writeSignals(List<String> signals) {
    try {
      return JSON.writeValueAsString(signals);
    } catch (JsonProcessingException notSerializable) {
      return "[]";
    }
  }

  static AlertDto toDto(Alert alert) {
    return new AlertDto(
        alert.getId(),
        alert.getTransaction() == null ? null : alert.getTransaction().getId(),
        alert.getType(),
        alert.getSeverity(),
        alert.getTitle(),
        alert.getMessage(),
        alert.getScore(),
        alert.getRecommendedAction(),
        alert.getAmount(),
        alert.getCurrency(),
        alert.getRecipientName(),
        readSignals(alert.getSignals()),
        alert.getCreatedAt());
  }

  private static List<String> readSignals(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return JSON.readValue(raw, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException unreadable) {
      return List.of();
    }
  }
}
