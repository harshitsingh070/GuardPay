package com.guardpay.risk.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * API request for {@code POST /api/risk/evaluate}.
 *
 * <p>Money is {@link BigDecimal} only. {@code historicalAverage} is optional —
 * a missing value means "insufficient history" and only skips the large-amount
 * signal (engine behavior, not duplicated here). Booleans default to
 * {@code false} when absent from the JSON.
 */
public class RiskEvaluationRequestDto {

  @NotNull(message = "amount is required")
  @DecimalMin(value = "0.00", message = "amount must be >= 0")
  private BigDecimal amount;

  @DecimalMin(value = "0.00", message = "historicalAverage must be >= 0")
  private BigDecimal historicalAverage;

  private boolean activeCall;
  private boolean newRecipient;
  private boolean unknownMerchant;
  private boolean unexpectedInvoice;
  private boolean urgentLanguage;
  private boolean suspiciousUrl;
  private boolean socialEngineeringDetected;

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getHistoricalAverage() {
    return historicalAverage;
  }

  public void setHistoricalAverage(BigDecimal historicalAverage) {
    this.historicalAverage = historicalAverage;
  }

  public boolean isActiveCall() {
    return activeCall;
  }

  public void setActiveCall(boolean activeCall) {
    this.activeCall = activeCall;
  }

  public boolean isNewRecipient() {
    return newRecipient;
  }

  public void setNewRecipient(boolean newRecipient) {
    this.newRecipient = newRecipient;
  }

  public boolean isUnknownMerchant() {
    return unknownMerchant;
  }

  public void setUnknownMerchant(boolean unknownMerchant) {
    this.unknownMerchant = unknownMerchant;
  }

  public boolean isUnexpectedInvoice() {
    return unexpectedInvoice;
  }

  public void setUnexpectedInvoice(boolean unexpectedInvoice) {
    this.unexpectedInvoice = unexpectedInvoice;
  }

  public boolean isUrgentLanguage() {
    return urgentLanguage;
  }

  public void setUrgentLanguage(boolean urgentLanguage) {
    this.urgentLanguage = urgentLanguage;
  }

  public boolean isSuspiciousUrl() {
    return suspiciousUrl;
  }

  public void setSuspiciousUrl(boolean suspiciousUrl) {
    this.suspiciousUrl = suspiciousUrl;
  }

  public boolean isSocialEngineeringDetected() {
    return socialEngineeringDetected;
  }

  public void setSocialEngineeringDetected(boolean socialEngineeringDetected) {
    this.socialEngineeringDetected = socialEngineeringDetected;
  }
}
