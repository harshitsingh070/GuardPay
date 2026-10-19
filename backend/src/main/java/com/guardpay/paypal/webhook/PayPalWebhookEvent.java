package com.guardpay.paypal.webhook;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Idempotency + audit record for PayPal webhook deliveries.
 *
 * <p>{@code paypalEventId} is globally unique: the first delivery processes,
 * repeats are acknowledged without reprocessing. {@code createdAt} (from
 * {@link BaseEntity}) is the receipt time.
 */
@Entity
@Table(name = "paypal_webhook_events")
public class PayPalWebhookEvent extends BaseEntity {

  @Column(name = "paypal_event_id", length = 128, nullable = false, unique = true)
  private String paypalEventId;

  @Column(name = "event_type", length = 64, nullable = false)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private WebhookProcessingStatus status;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  public String getPaypalEventId() {
    return paypalEventId;
  }

  public void setPaypalEventId(String paypalEventId) {
    this.paypalEventId = paypalEventId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public WebhookProcessingStatus getStatus() {
    return status;
  }

  public void setStatus(WebhookProcessingStatus status) {
    this.status = status;
  }

  public OffsetDateTime getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(OffsetDateTime processedAt) {
    this.processedAt = processedAt;
  }
}
