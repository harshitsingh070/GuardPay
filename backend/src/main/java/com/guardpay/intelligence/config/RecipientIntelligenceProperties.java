package com.guardpay.intelligence.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the shared recipient reputation store. HMAC secrets come
 * from the environment only; an empty map means the feature is unavailable
 * rather than silently unhashed. This is not a graph or a population-wide
 * fraud network: only per-recipient evidence is stored.
 */
@ConfigurationProperties(prefix = "guardpay.intelligence")
public class RecipientIntelligenceProperties {

  private boolean enabled = true;
  private Map<String, String> hmacKeys = new LinkedHashMap<>();
  private int activeKeyVersion = 1;
  private int reportRateLimitPerHour = 5;
  private int reportRateLimitPerRecipientPerDay = 1;
  private int evidenceHalfLifeDays = 180;
  private int evidenceRetentionDays = 365;
  private int staleCalculationHours = 24;
  private long maintenanceIntervalMs = 3_600_000L;
  private List<String> adminEmails = new ArrayList<>();

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

  public int getEvidenceRetentionDays() {
    return evidenceRetentionDays;
  }

  public void setEvidenceRetentionDays(int evidenceRetentionDays) {
    this.evidenceRetentionDays = evidenceRetentionDays;
  }

  public int getStaleCalculationHours() {
    return staleCalculationHours;
  }

  public void setStaleCalculationHours(int staleCalculationHours) {
    this.staleCalculationHours = staleCalculationHours;
  }

  public long getMaintenanceIntervalMs() {
    return maintenanceIntervalMs;
  }

  public void setMaintenanceIntervalMs(long maintenanceIntervalMs) {
    this.maintenanceIntervalMs = maintenanceIntervalMs;
  }

  public List<String> getAdminEmails() {
    return adminEmails;
  }

  public void setAdminEmails(List<String> adminEmails) {
    this.adminEmails = adminEmails == null ? new ArrayList<>() : adminEmails;
  }
}
