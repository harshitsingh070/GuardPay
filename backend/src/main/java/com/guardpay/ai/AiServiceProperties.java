package com.guardpay.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python AI service connection settings. The service is evidence-only and
 * optional: when disabled or unreachable, evaluations continue on
 * deterministic signals with an explicit unavailable status.
 */
@ConfigurationProperties(prefix = "guardpay.ai")
public class AiServiceProperties {

  /** Default matches local development (ai-service on 8001). */
  private String baseUrl = "http://localhost:8001";

  /** Operator kill-switch for AI evidence (deterministic-only mode). */
  private boolean enabled = true;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
