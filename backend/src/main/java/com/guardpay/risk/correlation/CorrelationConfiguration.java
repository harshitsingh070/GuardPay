package com.guardpay.risk.correlation;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tunable bounds for the correlation layer.
 *
 * <p>{@code freshnessFactor} is currently fixed at 1.0 because every signal
 * is collected for the live request. It exists so time-decayed evidence can be
 * added later without changing the scoring formula.
 */
@Configuration
@ConfigurationProperties(prefix = "guardpay.risk.correlation")
public class CorrelationConfiguration {

  private int maxContributionPoints = 25;
  private double interactionThreshold = 0.12;
  private double minimumSignalConfidence = 0.5;
  private double overlapThreshold = 0.6;
  private double dependencyDiscount = 0.5;
  private int familyContributionCap = 12;
  private double freshnessFactor = 1.0;
  private double strengthFloor = 0.05;
  private Map<String, Double> ruleWeights = new LinkedHashMap<>();

  public static CorrelationConfiguration defaults() {
    return new CorrelationConfiguration();
  }

  @PostConstruct
  void validate() {
    if (maxContributionPoints < 0) {
      throw new IllegalArgumentException("maxContributionPoints must not be negative");
    }
    if (familyContributionCap < 0) {
      throw new IllegalArgumentException("familyContributionCap must not be negative");
    }
    if (freshnessFactor < 0.0 || freshnessFactor > 1.0) {
      throw new IllegalArgumentException("freshnessFactor must be between 0 and 1");
    }
  }

  public int maxContributionPoints() {
    return maxContributionPoints;
  }

  public void setMaxContributionPoints(int maxContributionPoints) {
    this.maxContributionPoints = maxContributionPoints;
  }

  public double interactionThreshold() {
    return interactionThreshold;
  }

  public void setInteractionThreshold(double interactionThreshold) {
    this.interactionThreshold = interactionThreshold;
  }

  public double minimumSignalConfidence() {
    return minimumSignalConfidence;
  }

  public void setMinimumSignalConfidence(double minimumSignalConfidence) {
    this.minimumSignalConfidence = minimumSignalConfidence;
  }

  public double overlapThreshold() {
    return overlapThreshold;
  }

  public void setOverlapThreshold(double overlapThreshold) {
    this.overlapThreshold = overlapThreshold;
  }

  public double dependencyDiscount() {
    return dependencyDiscount;
  }

  public void setDependencyDiscount(double dependencyDiscount) {
    this.dependencyDiscount = dependencyDiscount;
  }

  public int familyContributionCap() {
    return familyContributionCap;
  }

  public void setFamilyContributionCap(int familyContributionCap) {
    this.familyContributionCap = familyContributionCap;
  }

  public double freshnessFactor() {
    return freshnessFactor;
  }

  public void setFreshnessFactor(double freshnessFactor) {
    this.freshnessFactor = freshnessFactor;
  }

  public double strengthFloor() {
    return strengthFloor;
  }

  public void setStrengthFloor(double strengthFloor) {
    this.strengthFloor = strengthFloor;
  }

  public Map<String, Double> ruleWeights() {
    return ruleWeights;
  }

  public void setRuleWeights(Map<String, Double> ruleWeights) {
    this.ruleWeights = ruleWeights != null ? new LinkedHashMap<>(ruleWeights) : new LinkedHashMap<>();
  }
}
