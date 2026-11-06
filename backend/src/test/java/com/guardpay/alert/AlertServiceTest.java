package com.guardpay.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.risk.signal.dto.PaymentSignalDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.DemoUserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

  @Mock private AlertRepository alerts;
  @Mock private NotificationOutboxRepository outbox;
  @Mock private DemoUserService demoUser;

  private AlertService service;

  @BeforeEach
  void setUp() {
    service = new AlertService(alerts, outbox, new AlertStreamService(), demoUser);
    lenient().when(outbox.existsByAlertIdAndChannel(any(), any())).thenReturn(false);
    lenient().when(alerts.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(outbox.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private static Transaction transaction(String amount, TransactionStatus status) {
    Transaction transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setAmount(new BigDecimal(amount));
    transaction.setCurrency("USD");
    transaction.setStatus(status);
    return transaction;
  }

  private static AutomaticEvaluationResponseDto risk(
      int score, RecommendedAction action, PaymentSignalType... signals) {
    List<PaymentSignalDto> automatic =
        List.of(signals).stream()
            .map(
                type ->
                    new PaymentSignalDto(type, true, SignalSource.DEVICE, null, "evidence"))
            .toList();
    return new AutomaticEvaluationResponseDto(
        score,
        score >= 85 ? RiskLevel.CRITICAL : score >= 70 ? RiskLevel.HIGH : RiskLevel.MEDIUM,
        action,
        "Explainable decision.",
        automatic,
        List.of(new RiskFindingDto("ACTIVE_CALL", "Active call", "desc", 15, true)),
        "NOT_REQUESTED",
        null);
  }

  @Test
  void stopCreatesCriticalAlertAndQueuesEveryChannel() {
    service.recordPaymentDecision(
        transaction("2499", TransactionStatus.CANCELLED),
        risk(100, RecommendedAction.STOP, PaymentSignalType.ACTIVE_CALL));

    ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
    verify(alerts).save(alert.capture());
    assertEquals(AlertSeverity.CRITICAL, alert.getValue().getSeverity());
    assertEquals(AlertType.PAYMENT_RISK, alert.getValue().getType());

    ArgumentCaptor<NotificationOutbox> rows =
        ArgumentCaptor.forClass(NotificationOutbox.class);
    verify(outbox, org.mockito.Mockito.times(3)).save(rows.capture());
    List<NotificationChannel> channels =
        rows.getAllValues().stream().map(NotificationOutbox::getChannel).toList();
    assertTrue(channels.contains(NotificationChannel.IN_APP));
    assertTrue(channels.contains(NotificationChannel.EMAIL));
    assertTrue(channels.contains(NotificationChannel.SMS));
  }

  @Test
  void highScoreVerifyOnlyQueuesInApp() {
    service.recordPaymentDecision(
        transaction("300", TransactionStatus.PENDING),
        risk(70, RecommendedAction.VERIFY));

    ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
    verify(alerts).save(alert.capture());
    assertEquals(AlertSeverity.HIGH, alert.getValue().getSeverity());
    verify(outbox, org.mockito.Mockito.times(1)).save(any(NotificationOutbox.class));
  }

  @Test
  void newDeviceHighValueRaisesWarning() {
    service.recordPaymentDecision(
        transaction("500", TransactionStatus.PENDING),
        risk(40, RecommendedAction.VERIFY, PaymentSignalType.NEW_DEVICE));

    ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
    verify(alerts).save(alert.capture());
    assertEquals(AlertSeverity.WARNING, alert.getValue().getSeverity());
  }

  @Test
  void lowRiskDecisionDoesNotCreateAlert() {
    service.recordPaymentDecision(
        transaction("40", TransactionStatus.PENDING),
        risk(20, RecommendedAction.ALLOW));

    verify(alerts, never()).save(any());
    verify(outbox, never()).save(any());
  }

  @Test
  void largeCaptureRequiresCompletedStatus() {
    service.recordLargeCapture(transaction("1500", TransactionStatus.FAILED));
    verify(alerts, never()).save(any());

    service.recordLargeCapture(transaction("1500", TransactionStatus.COMPLETED));
    ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
    verify(alerts).save(alert.capture());
    assertEquals(AlertType.LARGE_CAPTURE, alert.getValue().getType());
  }

  @Test
  void duplicateDecisionIsNotAlertedTwice() {
    when(alerts.existsByTransactionIdAndType(any(), any())).thenReturn(true);

    service.recordPaymentDecision(
        transaction("2499", TransactionStatus.CANCELLED),
        risk(100, RecommendedAction.STOP));

    verify(alerts, never()).save(any());
  }
}
