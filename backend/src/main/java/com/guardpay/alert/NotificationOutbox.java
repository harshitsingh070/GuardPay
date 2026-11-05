package com.guardpay.alert;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Transactional outbox row for one alert/channel pair. */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "alert_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_notification_outbox_alert"))
  private Alert alert;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", length = 16, nullable = false)
  private NotificationChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private NotificationStatus status = NotificationStatus.PENDING;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 512)
  private String lastError;

  @Column(name = "sent_at")
  private OffsetDateTime sentAt;

  public Alert getAlert() {
    return alert;
  }

  public void setAlert(Alert alert) {
    this.alert = alert;
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  public void setChannel(NotificationChannel channel) {
    this.channel = channel;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public OffsetDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(OffsetDateTime sentAt) {
    this.sentAt = sentAt;
  }
}
