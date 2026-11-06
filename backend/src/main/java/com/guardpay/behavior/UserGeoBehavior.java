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

/** Per-geo-country behavior for location anomaly detection. */
@Entity
@Table(
    name = "user_geo_behavior",
    uniqueConstraints = {
      @jakarta.persistence.UniqueConstraint(
          name = "uq_user_geo_behavior",
          columnNames = {"user_id", "geo_country"})
    })
public class UserGeoBehavior extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_geo_behavior_user"))
  private User user;

  @Column(name = "geo_country", length = 2, nullable = false)
  private String geoCountry;

  @Column(name = "transaction_count", nullable = false)
  private int transactionCount = 0;

  @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal totalAmount = BigDecimal.ZERO;

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

  public String getGeoCountry() {
    return geoCountry;
  }

  public void setGeoCountry(String geoCountry) {
    this.geoCountry = geoCountry;
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
