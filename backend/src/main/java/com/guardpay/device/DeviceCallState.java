package com.guardpay.device;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Latest reported call state for one device. Exactly one row per device
 * (upserted) — history is intentionally not kept.
 *
 * <p>Stores the binary signal only: device id, on-call flag, observation
 * time. Never phone numbers, contacts, audio, durations, or content.
 */
@Entity
@Table(name = "device_call_states")
public class DeviceCallState extends BaseEntity {

  @Column(name = "device_id", nullable = false, unique = true)
  private UUID deviceId;

  @Column(name = "active_call", nullable = false)
  private boolean activeCall;

  @Column(name = "observed_at", nullable = false)
  private OffsetDateTime observedAt;

  public UUID getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(UUID deviceId) {
    this.deviceId = deviceId;
  }

  public boolean isActiveCall() {
    return activeCall;
  }

  public void setActiveCall(boolean activeCall) {
    this.activeCall = activeCall;
  }

  public OffsetDateTime getObservedAt() {
    return observedAt;
  }

  public void setObservedAt(OffsetDateTime observedAt) {
    this.observedAt = observedAt;
  }
}
