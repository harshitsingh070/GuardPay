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
    boolean unknownMerchant,
    boolean unexpectedInvoice,
    boolean urgentLanguage,
    boolean suspiciousUrl,
    boolean socialEngineeringDetected) {}
