package com.guardpay.behavior;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.recipient.Recipient;
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

/** Per-recipient behavioral statistics for one user. */
@Entity
@Table(
    name = "user_recipient_behavior",
    uniqueConstraints = {
      @jakarta.persistence.UniqueConstraint(
          name = "uq_user_recipient_behavior",
          columnNames = {"user_id", "recipient_id"})
    })
public class UserRecipientBehavior extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_recipient_behavior_user"))
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_recipient_behavior_recipient"))
  private Recipient recipient;

  @Column(name = "transaction_count", nullable = false)
  private int transactionCount = 0;

  @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "avg_amount", precision = 19, scale = 4)
  private BigDecimal avgAmount = BigDecimal.ZERO;

  @Column(name = "median_amount", precision = 19, scale = 4)
  private BigDecimal medianAmount = BigDecimal.ZERO;

  @Column(name = "min_amount", precision = 19, scale = 4)
  private BigDecimal minAmount;

  @Column(name = "max_amount", precision = 19, scale = 4)
  private BigDecimal maxAmount;

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

  public Recipient getRecipient() {
    return recipient;
  }

  public void setRecipient(Recipient recipient) {
    this.recipient = recipient;
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

  public BigDecimal getAvgAmount() {
    return avgAmount;
  }

  public void setAvgAmount(BigDecimal avgAmount) {
    this.avgAmount = avgAmount;
  }

  public BigDecimal getMedianAmount() {
    return medianAmount;
  }

  public void setMedianAmount(BigDecimal medianAmount) {
    this.medianAmount = medianAmount;
  }

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public void setMinAmount(BigDecimal minAmount) {
    this.minAmount = minAmount;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(BigDecimal maxAmount) {
    this.maxAmount = maxAmount;
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
