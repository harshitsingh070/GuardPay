package com.guardpay.ai.dto;

import java.math.BigDecimal;

/**
 * Internal AI-analysis request (Spring Boot → Python only, never browsers).
 * Minimum context for scam analysis: no emails, phone numbers, audio, or
 * credentials.
 */
public record AiAnalysisRequest(
    AiTransactionContext transaction, AiContentContext content, AiDeviceContext device) {

  public record AiTransactionContext(
      BigDecimal amount,
      String currency,
      String merchantName,
      String recipientName,
      boolean isNewRecipient,
      boolean isUnknownMerchant,
      boolean unexpectedInvoice) {}

  public record AiContentContext(String text, String url) {}

  public record AiDeviceContext(Boolean activeCall) {}
}
