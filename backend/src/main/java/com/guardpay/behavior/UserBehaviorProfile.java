package com.guardpay.behavior;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
/**
 * Per-user behavioral profile — the persisted learned baseline.
 *
 * <p>One row per user. Statistics are recomputed and persisted after each
 * eligible transaction outcome. The profile is reusable across payment
 * evaluations instead of recomputing from raw history every time.
 */
@Entity
@Table(name = "user_behavior_profiles")
public class UserBehaviorProfile extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_behavior_profiles_user"))
  private User user;

  @Version
  @Column(name = "version", nullable = false)
  private int version = 1;

  @Column(name = "profile_version", nullable = false)
  private int profileVersion = 1;

  @Enumerated(EnumType.STRING)
  @Column(name = "profile_status", length = 32, nullable = false)
  private ProfileStatus profileStatus = ProfileStatus.INSUFFICIENT_DATA;

  @Column(name = "transaction_count", nullable = false)
  private int transactionCount = 0;

  @Column(name = "first_observed_at")
  private OffsetDateTime firstObservedAt;

  @Column(name = "last_updated_at", nullable = false)
  private OffsetDateTime lastUpdatedAt = OffsetDateTime.now();

  @Column(name = "data_quality_score", precision = 3, scale = 2, nullable = false)
  private BigDecimal dataQualityScore = BigDecimal.ZERO;

  @Column(name = "amount_min", precision = 19, scale = 4)
  private BigDecimal amountMin;

  @Column(name = "amount_max", precision = 19, scale = 4)
  private BigDecimal amountMax;

  @Column(name = "amount_mean", precision = 19, scale = 4)
  private BigDecimal amountMean;

  @Column(name = "amount_median", precision = 19, scale = 4)
  private BigDecimal amountMedian;

  @Column(name = "amount_stddev", precision = 19, scale = 4)
  private BigDecimal amountStddev;

  @Column(name = "amount_mad", precision = 19, scale = 4)
  private BigDecimal amountMad;

  @Column(name = "amount_p90", precision = 19, scale = 4)
  private BigDecimal amountP90;

  @Column(name = "amount_p95", precision = 19, scale = 4)
  private BigDecimal amountP95;

  @Column(name = "frequency_per_hour", precision = 9, scale = 4)
  private BigDecimal frequencyPerHour;

  @Column(name = "frequency_per_day", precision = 9, scale = 4)
  private BigDecimal frequencyPerDay;

  @Column(name = "frequency_per_week", precision = 9, scale = 4)
  private BigDecimal frequencyPerWeek;

  @Column(name = "avg_interval_minutes", precision = 12, scale = 4)
  private BigDecimal avgIntervalMinutes;

  @Column(name = "peak_hour")
  private Integer peakHour;

  @Column(name = "weekend_payment_ratio", precision = 5, scale = 4)
  private BigDecimal weekendPaymentRatio;

  @Column(name = "night_payment_ratio", precision = 5, scale = 4)
  private BigDecimal nightPaymentRatio;

  @Column(name = "unique_recipient_count", nullable = false)
  private int uniqueRecipientCount = 0;

  @Column(name = "established_recipient_count", nullable = false)
  private int establishedRecipientCount = 0;

  @Column(name = "new_recipient_ratio", precision = 5, scale = 4)
  private BigDecimal newRecipientRatio;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "timezone", length = 64)
  private String timezone;

  @Column(name = "last_included_transaction_id")
  private UUID lastIncludedTransactionId;

  @Column(name = "device_count", nullable = false)
  private int deviceCount = 0;

  @Column(name = "geo_country_count", nullable = false)
  private int geoCountryCount = 0;

  @Column(name = "profile_metadata", columnDefinition = "TEXT")
  private String profileMetadata;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public UUID getLastIncludedTransactionId() {
    return lastIncludedTransactionId;
  }

  public void setLastIncludedTransactionId(UUID lastIncludedTransactionId) {
    this.lastIncludedTransactionId = lastIncludedTransactionId;
  }

  public int getProfileVersion() {
    return profileVersion;
  }

  public void setProfileVersion(int profileVersion) {
    this.profileVersion = profileVersion;
  }

  public ProfileStatus getProfileStatus() {
    return profileStatus;
  }

  public void setProfileStatus(ProfileStatus profileStatus) {
    this.profileStatus = profileStatus;
  }

  public int getTransactionCount() {
    return transactionCount;
  }

  public void setTransactionCount(int transactionCount) {
    this.transactionCount = transactionCount;
  }

  public OffsetDateTime getFirstObservedAt() {
    return firstObservedAt;
  }

  public void setFirstObservedAt(OffsetDateTime firstObservedAt) {
    this.firstObservedAt = firstObservedAt;
  }

  public OffsetDateTime getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  public BigDecimal getDataQualityScore() {
    return dataQualityScore;
  }

  public void setDataQualityScore(BigDecimal dataQualityScore) {
    this.dataQualityScore = dataQualityScore;
  }

  public BigDecimal getAmountMin() {
    return amountMin;
  }

  public void setAmountMin(BigDecimal amountMin) {
    this.amountMin = amountMin;
  }

  public BigDecimal getAmountMax() {
    return amountMax;
  }

  public void setAmountMax(BigDecimal amountMax) {
    this.amountMax = amountMax;
  }

  public BigDecimal getAmountMean() {
    return amountMean;
  }

  public void setAmountMean(BigDecimal amountMean) {
    this.amountMean = amountMean;
  }

  public BigDecimal getAmountMedian() {
    return amountMedian;
  }

  public void setAmountMedian(BigDecimal amountMedian) {
    this.amountMedian = amountMedian;
  }

  public BigDecimal getAmountStddev() {
    return amountStddev;
  }

  public void setAmountStddev(BigDecimal amountStddev) {
    this.amountStddev = amountStddev;
  }

  public BigDecimal getAmountMad() {
    return amountMad;
  }

  public void setAmountMad(BigDecimal amountMad) {
    this.amountMad = amountMad;
  }

  public BigDecimal getAmountP90() {
    return amountP90;
  }

  public void setAmountP90(BigDecimal amountP90) {
    this.amountP90 = amountP90;
  }

  public BigDecimal getAmountP95() {
    return amountP95;
  }

  public void setAmountP95(BigDecimal amountP95) {
    this.amountP95 = amountP95;
  }

  public BigDecimal getFrequencyPerHour() {
    return frequencyPerHour;
  }

  public void setFrequencyPerHour(BigDecimal frequencyPerHour) {
    this.frequencyPerHour = frequencyPerHour;
  }

  public BigDecimal getFrequencyPerDay() {
    return frequencyPerDay;
  }

  public void setFrequencyPerDay(BigDecimal frequencyPerDay) {
    this.frequencyPerDay = frequencyPerDay;
  }

  public BigDecimal getFrequencyPerWeek() {
    return frequencyPerWeek;
  }

  public void setFrequencyPerWeek(BigDecimal frequencyPerWeek) {
    this.frequencyPerWeek = frequencyPerWeek;
  }

  public BigDecimal getAvgIntervalMinutes() {
    return avgIntervalMinutes;
  }

  public void setAvgIntervalMinutes(BigDecimal avgIntervalMinutes) {
    this.avgIntervalMinutes = avgIntervalMinutes;
  }

  public Integer getPeakHour() {
    return peakHour;
  }

  public void setPeakHour(Integer peakHour) {
    this.peakHour = peakHour;
  }

  public BigDecimal getWeekendPaymentRatio() {
    return weekendPaymentRatio;
  }

  public void setWeekendPaymentRatio(BigDecimal weekendPaymentRatio) {
    this.weekendPaymentRatio = weekendPaymentRatio;
  }

  public BigDecimal getNightPaymentRatio() {
    return nightPaymentRatio;
  }

  public void setNightPaymentRatio(BigDecimal nightPaymentRatio) {
    this.nightPaymentRatio = nightPaymentRatio;
  }

  public int getUniqueRecipientCount() {
    return uniqueRecipientCount;
  }

  public void setUniqueRecipientCount(int uniqueRecipientCount) {
    this.uniqueRecipientCount = uniqueRecipientCount;
  }

  public int getEstablishedRecipientCount() {
    return establishedRecipientCount;
  }

  public void setEstablishedRecipientCount(int establishedRecipientCount) {
    this.establishedRecipientCount = establishedRecipientCount;
  }

  public BigDecimal getNewRecipientRatio() {
    return newRecipientRatio;
  }

  public void setNewRecipientRatio(BigDecimal newRecipientRatio) {
    this.newRecipientRatio = newRecipientRatio;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public int getDeviceCount() {
    return deviceCount;
  }

  public void setDeviceCount(int deviceCount) {
    this.deviceCount = deviceCount;
  }

  public int getGeoCountryCount() {
    return geoCountryCount;
  }

  public void setGeoCountryCount(int geoCountryCount) {
    this.geoCountryCount = geoCountryCount;
  }

  public String getProfileMetadata() {
    return profileMetadata;
  }

  public void setProfileMetadata(String profileMetadata) {
    this.profileMetadata = profileMetadata;
  }

  public enum ProfileStatus {
    INSUFFICIENT_DATA,
    INITIALIZING,
    ESTABLISHED,
    DEGRADED
  }
}
