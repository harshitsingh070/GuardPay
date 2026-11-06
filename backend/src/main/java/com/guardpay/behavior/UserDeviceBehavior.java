package com.guardpay.behavior;

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

/** Per-device behavior for device anomaly detection. */
@Entity
@Table(
    name = "user_device_behavior",
    uniqueConstraints = {
      @jakarta.persistence.UniqueConstraint(
          name = "uq_user_device_behavior",
          columnNames = {"user_id", "device_id", "device_fingerprint"})
    })
public class UserDeviceBehavior extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_device_behavior_user"))
  private User user;

  @Column(name = "device_id")
  private UUID deviceId;

  @Column(name = "device_fingerprint", length = 128)
  private String deviceFingerprint;

  @Column(name = "transaction_count", nullable = false)
  private int transactionCount = 0;

  @Column(name = "first_seen_at")
  private OffsetDateTime firstSeenAt = OffsetDateTime.now();

  @Column(name = "last_seen_at")
  private OffsetDateTime lastSeenAt = OffsetDateTime.now();

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

  public String getDeviceFingerprint() {
    return deviceFingerprint;
  }

  public void setDeviceFingerprint(String deviceFingerprint) {
    this.deviceFingerprint = deviceFingerprint;
  }

  public int getTransactionCount() {
    return transactionCount;
  }

  public void setTransactionCount(int transactionCount) {
    this.transactionCount = transactionCount;
  }

  public OffsetDateTime getFirstSeenAt() {
    return firstSeenAt;
  }

  public void setFirstSeenAt(OffsetDateTime firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
  }

  public OffsetDateTime getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(OffsetDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }
}
