package com.guardpay.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Internal AI-analysis request (Spring Boot → Python only, never browsers).
 * Wire format is snake_case, matching the Python Pydantic contract. Minimum
 * context for scam analysis: no emails, phone numbers, audio, or credentials.
 */
public record AiAnalysisRequest(
    AiTransactionContext transaction, AiContentContext content, AiDeviceContext device) {

  public record AiTransactionContext(
      BigDecimal amount,
      String currency,
      @JsonProperty("merchant_name") String merchantName,
      @JsonProperty("recipient_name") String recipientName,
      @JsonProperty("is_new_recipient") boolean isNewRecipient,
      @JsonProperty("is_unknown_merchant") boolean isUnknownMerchant,
      @JsonProperty("unexpected_invoice") boolean unexpectedInvoice) {}

  public record AiContentContext(String text, String url) {}

  public record AiDeviceContext(@JsonProperty("active_call") Boolean activeCall) {}
}
