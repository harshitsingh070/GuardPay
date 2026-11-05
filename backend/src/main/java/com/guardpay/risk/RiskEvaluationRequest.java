package com.guardpay.risk;

import java.math.BigDecimal;

/**
 * Immutable input for the deterministic risk engine.
 *
 * <p>Plain data — no HTTP, no JPA, no AI. A {@code null} (or non-positive)
 * {@code historicalAverage} means "insufficient history": the large-amount
 * signal is skipped while every other signal is still evaluated.
 */
public record RiskEvaluationRequest(
    BigDecimal currentAmount,
    BigDecimal historicalAverage,
    boolean activeCall,
    boolean newRecipient,
    boolean repeatedAttempts,
    boolean unknownMerchant,
    boolean unexpectedInvoice,
    boolean urgentLanguage,
    boolean suspiciousUrl,
    boolean socialEngineeringDetected,
    boolean missingContext,
    boolean recipientAmountAnomaly,
    boolean newDevice,
    boolean newGeo,
    boolean impossibleTravel,
    boolean vpnProxy,
    boolean hourlyVelocity,
    boolean dailyVelocity,
    boolean blocklistedRecipient,
    boolean knownRecipientScam,
    boolean splitPayment) {

  /** Backwards-compatible constructor; newly added policy signals default to false. */
  public RiskEvaluationRequest(
      BigDecimal currentAmount,
      BigDecimal historicalAverage,
      boolean activeCall,
      boolean newRecipient,
      boolean repeatedAttempts,
      boolean unknownMerchant,
      boolean unexpectedInvoice,
      boolean urgentLanguage,
      boolean suspiciousUrl,
      boolean socialEngineeringDetected,
      boolean missingContext,
      boolean recipientAmountAnomaly,
      boolean newDevice,
      boolean newGeo,
      boolean impossibleTravel,
      boolean vpnProxy,
      boolean hourlyVelocity,
      boolean dailyVelocity,
      boolean blocklistedRecipient) {
    this(
        currentAmount,
        historicalAverage,
        activeCall,
        newRecipient,
        repeatedAttempts,
        unknownMerchant,
        unexpectedInvoice,
        urgentLanguage,
        suspiciousUrl,
        socialEngineeringDetected,
        missingContext,
        recipientAmountAnomaly,
        newDevice,
        newGeo,
        impossibleTravel,
        vpnProxy,
        hourlyVelocity,
        dailyVelocity,
        blocklistedRecipient,
        false,
        false);
  }

  /** Backwards-compatible constructor; account-takeover/velocity signals default to false. */
  public RiskEvaluationRequest(
      BigDecimal currentAmount,
      BigDecimal historicalAverage,
      boolean activeCall,
      boolean newRecipient,
      boolean repeatedAttempts,
      boolean unknownMerchant,
      boolean unexpectedInvoice,
      boolean urgentLanguage,
      boolean suspiciousUrl,
      boolean socialEngineeringDetected,
      boolean missingContext,
      boolean recipientAmountAnomaly,
      boolean newDevice,
      boolean newGeo,
      boolean impossibleTravel,
      boolean vpnProxy) {
    this(
        currentAmount,
        historicalAverage,
        activeCall,
        newRecipient,
        repeatedAttempts,
        unknownMerchant,
        unexpectedInvoice,
        urgentLanguage,
        suspiciousUrl,
        socialEngineeringDetected,
        missingContext,
        recipientAmountAnomaly,
        newDevice,
        newGeo,
        impossibleTravel,
        vpnProxy,
        false,
        false,
        false,
        false,
        false);
  }

  /** Backwards-compatible constructor; account-takeover signals default to false. */
  public RiskEvaluationRequest(
      BigDecimal currentAmount,
      BigDecimal historicalAverage,
      boolean activeCall,
      boolean newRecipient,
      boolean repeatedAttempts,
      boolean unknownMerchant,
      boolean unexpectedInvoice,
      boolean urgentLanguage,
      boolean suspiciousUrl,
      boolean socialEngineeringDetected,
      boolean missingContext,
      boolean recipientAmountAnomaly) {
    this(
        currentAmount,
        historicalAverage,
        activeCall,
        newRecipient,
        repeatedAttempts,
        unknownMerchant,
        unexpectedInvoice,
        urgentLanguage,
        suspiciousUrl,
        socialEngineeringDetected,
        missingContext,
        recipientAmountAnomaly,
        false,
        false,
        false,
        false);
  }

  /** Backwards-compatible constructor; recipient amount anomaly defaults to false. */
  public RiskEvaluationRequest(
      BigDecimal currentAmount,
      BigDecimal historicalAverage,
      boolean activeCall,
      boolean newRecipient,
      boolean repeatedAttempts,
      boolean unknownMerchant,
      boolean unexpectedInvoice,
      boolean urgentLanguage,
      boolean suspiciousUrl,
      boolean socialEngineeringDetected,
      boolean missingContext) {
    this(
        currentAmount,
        historicalAverage,
        activeCall,
        newRecipient,
        repeatedAttempts,
        unknownMerchant,
        unexpectedInvoice,
        urgentLanguage,
        suspiciousUrl,
        socialEngineeringDetected,
        missingContext,
        false);
  }

  /** Backwards-compatible constructor; missing context defaults to false. */
  public RiskEvaluationRequest(
      BigDecimal currentAmount,
      BigDecimal historicalAverage,
      boolean activeCall,
      boolean newRecipient,
      boolean repeatedAttempts,
      boolean unknownMerchant,
      boolean unexpectedInvoice,
      boolean urgentLanguage,
      boolean suspiciousUrl,
      boolean socialEngineeringDetected) {
    this(
        currentAmount,
        historicalAverage,
        activeCall,
        newRecipient,
        repeatedAttempts,
        unknownMerchant,
        unexpectedInvoice,
        urgentLanguage,
        suspiciousUrl,
        socialEngineeringDetected,
        false);
  }
}
