package com.guardpay.behavior.highvalue;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized configuration for the First-Time High-Value Payment Detection Engine.
 *
 * <p>All statistical thresholds, ratios, escalation factors, and confidence bounds
 * are parameterized here rather than scattered across service classes.
 *
 * <p>Default rationales:
 * <ul>
 *   <li>{@code minTransactionsForBaseline} = 5: Minimum sample size required to estimate
 *       median and MAD without severe sample distortion.</li>
 *   <li>{@code coldStartThreshold} = 10: Accounts with fewer than 10 transactions have
 *       preliminary profiles; confidence is scaled down so missing history is never punished.</li>
 *   <li>{@code establishedThreshold} = 20: Accounts with 20+ transactions have established
 *       baselines with stable percentile distributions.</li>
 *   <li>{@code minMeaningfulDeviationRatio} = 1.5: Protects against flagging normal daily
 *       spending fluctuations (e.g. $10 vs $12).</li>
 *   <li>{@code modifiedZSignificantThreshold} = 3.0: Standard Boris Iglewicz & David Hoaglin
 *       recommendation (Z &gt; 3.0 indicates statistically significant outlier).</li>
 *   <li>{@code modifiedZExtremeThreshold} = 4.5: Indicates extreme outlier requiring verification/stop.</li>
 *   <li>{@code percentileHighValue} = 0.90: Identifies payments in the top decile of user history.</li>
 *   <li>{@code percentileExtreme} = 0.95: Identifies payments in the top 5% of user history.</li>
 *   <li>{@code historicalMaxEscalationFactor} = 1.25: A payment exceeding the historical maximum
 *       by 25%+ is a noteworthy escalation.</li>
 *   <li>{@code recipientEscalationFactor} = 3.0: A payment 3x+ higher than the historical median
 *       sent to an existing recipient represents a recipient-specific anomaly.</li>
 *   <li>{@code rapidEscalationWindowDays} = 7: Recent window examined for sudden spikes.</li>
 *   <li>{@code rapidEscalationFactor} = 4.0: Jump of 4x over recent 7-day average indicates
 *       rapid escalation.</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "guardpay.risk.high-value")
public class FirstTimeHighValueConfig {

  /**
   * Kill switch used by the benchmark and operations. When false the engine
   * reports a cold-start assessment and never emits a finding.
   */
  private boolean enabled = true;

  private int minTransactionsForBaseline = 5;
  private int coldStartThreshold = 10;
  private int establishedThreshold = 20;

  private BigDecimal minMeaningfulDeviationRatio = new BigDecimal("1.50");
  private BigDecimal modifiedZSignificantThreshold = new BigDecimal("3.00");
  private BigDecimal modifiedZExtremeThreshold = new BigDecimal("4.50");
  private BigDecimal zeroMadThreshold = new BigDecimal("0.01");
  private BigDecimal zeroMadSignificantRatio = new BigDecimal("2.50");
  private BigDecimal amountWithinScaleZThreshold = new BigDecimal("2.50");
  private BigDecimal normalAmountRatioCeiling = new BigDecimal("2.00");

  private double percentileHighValue = 0.90;
  private double percentileExtreme = 0.95;

  private BigDecimal historicalMaxEscalationFactor = new BigDecimal("1.25");
  private BigDecimal recipientEscalationFactor = new BigDecimal("3.00");
  private BigDecimal recipientMaxEscalationFactor = new BigDecimal("2.00");
  private BigDecimal recipientMinModifiedZ = new BigDecimal("2.00");
  private BigDecimal newRecipientCriticalRatio = new BigDecimal("5.00");
  private BigDecimal recipientCriticalRatio = new BigDecimal("4.00");
  private BigDecimal extremeRatioOverMax = new BigDecimal("2.00");

  private int recentWindowDays = 7;
  private int mediumWindowDays = 30;
  private int longWindowDays = 90;
  private int rapidEscalationWindowDays = 7;
  private BigDecimal rapidEscalationFactor = new BigDecimal("4.00");

  private int maxSignalContribution = 25;
  private double confidenceDiscountColdStart = 0.40;
  private double confidenceInitializing = 0.70;
  private double confidenceEstablished = 0.90;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public static FirstTimeHighValueConfig defaults() {
    return new FirstTimeHighValueConfig();
  }

  public int getMinTransactionsForBaseline() {
    return minTransactionsForBaseline;
  }

  public void setMinTransactionsForBaseline(int minTransactionsForBaseline) {
    this.minTransactionsForBaseline = minTransactionsForBaseline;
  }

  public int getColdStartThreshold() {
    return coldStartThreshold;
  }

  public void setColdStartThreshold(int coldStartThreshold) {
    this.coldStartThreshold = coldStartThreshold;
  }

  public int getEstablishedThreshold() {
    return establishedThreshold;
  }

  public void setEstablishedThreshold(int establishedThreshold) {
    this.establishedThreshold = establishedThreshold;
  }

  public BigDecimal getMinMeaningfulDeviationRatio() {
    return minMeaningfulDeviationRatio;
  }

  public void setMinMeaningfulDeviationRatio(BigDecimal minMeaningfulDeviationRatio) {
    this.minMeaningfulDeviationRatio = minMeaningfulDeviationRatio;
  }

  public BigDecimal getModifiedZSignificantThreshold() {
    return modifiedZSignificantThreshold;
  }

  public void setModifiedZSignificantThreshold(BigDecimal modifiedZSignificantThreshold) {
    this.modifiedZSignificantThreshold = modifiedZSignificantThreshold;
  }

  public BigDecimal getModifiedZExtremeThreshold() {
    return modifiedZExtremeThreshold;
  }

  public void setModifiedZExtremeThreshold(BigDecimal modifiedZExtremeThreshold) {
    this.modifiedZExtremeThreshold = modifiedZExtremeThreshold;
  }

  public BigDecimal getZeroMadThreshold() {
    return zeroMadThreshold;
  }

  public void setZeroMadThreshold(BigDecimal zeroMadThreshold) {
    this.zeroMadThreshold = zeroMadThreshold;
  }

  public double getPercentileHighValue() {
    return percentileHighValue;
  }

  public void setPercentileHighValue(double percentileHighValue) {
    this.percentileHighValue = percentileHighValue;
  }

  public double getPercentileExtreme() {
    return percentileExtreme;
  }

  public void setPercentileExtreme(double percentileExtreme) {
    this.percentileExtreme = percentileExtreme;
  }

  public BigDecimal getHistoricalMaxEscalationFactor() {
    return historicalMaxEscalationFactor;
  }

  public void setHistoricalMaxEscalationFactor(BigDecimal historicalMaxEscalationFactor) {
    this.historicalMaxEscalationFactor = historicalMaxEscalationFactor;
  }

  public BigDecimal getRecipientEscalationFactor() {
    return recipientEscalationFactor;
  }

  public void setRecipientEscalationFactor(BigDecimal recipientEscalationFactor) {
    this.recipientEscalationFactor = recipientEscalationFactor;
  }

  public BigDecimal getRecipientMaxEscalationFactor() {
    return recipientMaxEscalationFactor;
  }

  public void setRecipientMaxEscalationFactor(BigDecimal recipientMaxEscalationFactor) {
    this.recipientMaxEscalationFactor = recipientMaxEscalationFactor;
  }

  public int getRapidEscalationWindowDays() {
    return rapidEscalationWindowDays;
  }

  public void setRapidEscalationWindowDays(int rapidEscalationWindowDays) {
    this.rapidEscalationWindowDays = rapidEscalationWindowDays;
  }

  public BigDecimal getRapidEscalationFactor() {
    return rapidEscalationFactor;
  }

  public void setRapidEscalationFactor(BigDecimal rapidEscalationFactor) {
    this.rapidEscalationFactor = rapidEscalationFactor;
  }

  public int getMaxSignalContribution() {
    return maxSignalContribution;
  }

  public void setMaxSignalContribution(int maxSignalContribution) {
    this.maxSignalContribution = maxSignalContribution;
  }

  public double getConfidenceDiscountColdStart() {
    return confidenceDiscountColdStart;
  }

  public void setConfidenceDiscountColdStart(double confidenceDiscountColdStart) {
    this.confidenceDiscountColdStart = confidenceDiscountColdStart;
  }

  public double getConfidenceInitializing() {
    return confidenceInitializing;
  }

  public void setConfidenceInitializing(double confidenceInitializing) {
    this.confidenceInitializing = confidenceInitializing;
  }

  public double getConfidenceEstablished() {
    return confidenceEstablished;
  }

  public void setConfidenceEstablished(double confidenceEstablished) {
    this.confidenceEstablished = confidenceEstablished;
  }

  public BigDecimal getZeroMadSignificantRatio() {
    return zeroMadSignificantRatio;
  }

  public void setZeroMadSignificantRatio(BigDecimal zeroMadSignificantRatio) {
    this.zeroMadSignificantRatio = zeroMadSignificantRatio;
  }

  public BigDecimal getAmountWithinScaleZThreshold() {
    return amountWithinScaleZThreshold;
  }

  public void setAmountWithinScaleZThreshold(BigDecimal amountWithinScaleZThreshold) {
    this.amountWithinScaleZThreshold = amountWithinScaleZThreshold;
  }

  public BigDecimal getNormalAmountRatioCeiling() {
    return normalAmountRatioCeiling;
  }

  public void setNormalAmountRatioCeiling(BigDecimal normalAmountRatioCeiling) {
    this.normalAmountRatioCeiling = normalAmountRatioCeiling;
  }

  public BigDecimal getRecipientMinModifiedZ() {
    return recipientMinModifiedZ;
  }

  public void setRecipientMinModifiedZ(BigDecimal recipientMinModifiedZ) {
    this.recipientMinModifiedZ = recipientMinModifiedZ;
  }

  public BigDecimal getNewRecipientCriticalRatio() {
    return newRecipientCriticalRatio;
  }

  public void setNewRecipientCriticalRatio(BigDecimal newRecipientCriticalRatio) {
    this.newRecipientCriticalRatio = newRecipientCriticalRatio;
  }

  public BigDecimal getRecipientCriticalRatio() {
    return recipientCriticalRatio;
  }

  public void setRecipientCriticalRatio(BigDecimal recipientCriticalRatio) {
    this.recipientCriticalRatio = recipientCriticalRatio;
  }

  public BigDecimal getExtremeRatioOverMax() {
    return extremeRatioOverMax;
  }

  public void setExtremeRatioOverMax(BigDecimal extremeRatioOverMax) {
    this.extremeRatioOverMax = extremeRatioOverMax;
  }

  public int getRecentWindowDays() {
    return recentWindowDays;
  }

  public void setRecentWindowDays(int recentWindowDays) {
    this.recentWindowDays = recentWindowDays;
  }

  public int getMediumWindowDays() {
    return mediumWindowDays;
  }

  public void setMediumWindowDays(int mediumWindowDays) {
    this.mediumWindowDays = mediumWindowDays;
  }

  public int getLongWindowDays() {
    return longWindowDays;
  }

  public void setLongWindowDays(int longWindowDays) {
    this.longWindowDays = longWindowDays;
  }
}

