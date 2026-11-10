package com.guardpay.intelligence.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the recipient reputation and global fraud intelligence
 * network. HMAC secrets come from the environment only; an empty map means
 * the feature is unavailable rather than silently unhashed.
 */
@ConfigurationProperties(prefix = "guardpay.intelligence")
public class RecipientIntelligenceProperties {

  private boolean enabled = true;
  private Map<String, String> hmacKeys = new LinkedHashMap<>();
  private int activeKeyVersion = 1;
  private int reportRateLimitPerHour = 5;
  private int reportRateLimitPerRecipientPerDay = 1;
  private int evidenceHalfLifeDays = 180;
  private int maxDescriptionLength = 1000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, String> getHmacKeys() {
    return hmacKeys;
  }

  public void setHmacKeys(Map<String, String> hmacKeys) {
    this.hmacKeys = hmacKeys == null ? new LinkedHashMap<>() : hmacKeys;
  }

  public int getActiveKeyVersion() {
    return activeKeyVersion;
  }

  public void setActiveKeyVersion(int activeKeyVersion) {
    this.activeKeyVersion = activeKeyVersion;
  }

  public int getReportRateLimitPerHour() {
    return reportRateLimitPerHour;
  }

  public void setReportRateLimitPerHour(int reportRateLimitPerHour) {
    this.reportRateLimitPerHour = reportRateLimitPerHour;
  }

  public int getReportRateLimitPerRecipientPerDay() {
    return reportRateLimitPerRecipientPerDay;
  }

  public void setReportRateLimitPerRecipientPerDay(int reportRateLimitPerRecipientPerDay) {
    this.reportRateLimitPerRecipientPerDay = reportRateLimitPerRecipientPerDay;
  }

  public int getEvidenceHalfLifeDays() {
    return evidenceHalfLifeDays;
  }

  public void setEvidenceHalfLifeDays(int evidenceHalfLifeDays) {
    this.evidenceHalfLifeDays = evidenceHalfLifeDays;
  }

  public int getMaxDescriptionLength() {
    return maxDescriptionLength;
  }

  public void setMaxDescriptionLength(int maxDescriptionLength) {
    this.maxDescriptionLength = maxDescriptionLength;
  }
}
