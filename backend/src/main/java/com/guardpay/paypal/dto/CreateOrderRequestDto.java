package com.guardpay.paypal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * API request for {@code POST /api/paypal/orders}.
 *
 * <p>Carries the payment amount plus the same risk-context signals as the
 * evaluation API so the backend can re-run the engine server-side. Never
 * carries credentials, tokens, or client decisions.
 */
public class CreateOrderRequestDto {

  @NotNull(message = "amount is required")
  @DecimalMin(value = "0.01", message = "amount must be greater than 0")
  @DecimalMax(value = "100000.00", message = "amount exceeds the Sandbox demo maximum")
  private BigDecimal amount;

  @NotBlank(message = "currency is required")
  @Pattern(regexp = "USD", message = "currency must be USD for this milestone")
  private String currency;

  @Size(max = 255, message = "description must be 255 characters or fewer")
  private String description;

  @DecimalMin(value = "0.00", message = "historicalAverage must be >= 0")
  private BigDecimal historicalAverage;

  private boolean activeCall;
  private boolean newRecipient;
  private boolean repeatedAttempts;
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

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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

  public boolean isRepeatedAttempts() {
    return repeatedAttempts;
  }

  public void setRepeatedAttempts(boolean repeatedAttempts) {
    this.repeatedAttempts = repeatedAttempts;
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
