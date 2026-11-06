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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Per-hour payment distribution (0-23) for time-of-day anomaly detection. */
@Entity
@Table(
    name = "user_hourly_behavior",
    uniqueConstraints = {
      @jakarta.persistence.UniqueConstraint(
          name = "uq_user_hourly_behavior",
          columnNames = {"user_id", "hour_of_day"})
    })
public class UserHourlyBehavior extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_hourly_behavior_user"))
  private User user;

  @Column(name = "hour_of_day", nullable = false)
  private int hourOfDay;

  @Column(name = "transaction_count", nullable = false)
  private int transactionCount = 0;

  @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "last_updated_at", nullable = false)
  private OffsetDateTime lastUpdatedAt = OffsetDateTime.now();

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public int getHourOfDay() {
    return hourOfDay;
  }

  public void setHourOfDay(int hourOfDay) {
    this.hourOfDay = hourOfDay;
  }

  public int getTransactionCount() {
    return transactionCount;
  }

  public void setTransactionCount(int transactionCount) {
    this.transactionCount = transactionCount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public OffsetDateTime getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }
}
