package com.guardpay.device;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_device_call_states_user"))
  private User user;

  @Column(name = "device_id", nullable = false, unique = true)
  private UUID deviceId;

  @Column(name = "active_call", nullable = false)
  private boolean activeCall;

  @Column(name = "observed_at", nullable = false)
  private OffsetDateTime observedAt;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

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
