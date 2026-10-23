package com.guardpay.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Idempotency replay support: canonical request hashing and stored-response
 * decoding. A reused key with different content is always a conflict.
 */
final class GuardedPaymentReplay {

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private GuardedPaymentReplay() {}

  static String requestHash(GuardedPaymentRequestDto dto) {
    String canonical =
        String.join(
            "|",
            dto.getAmount().stripTrailingZeros().toPlainString(),
            nullToEmpty(dto.getCurrency()),
            nullToEmpty(dto.getRecipientId()),
            lowerOrEmpty(dto.getRecipientName()),
            lowerOrEmpty(dto.getRecipientEmail()),
            nullToEmpty(dto.getRecipientPaypalAccountId()),
            nullToEmpty(dto.getMerchantId()),
            lowerOrEmpty(dto.getMerchantName()),
            nullToEmpty(dto.getDeviceId()),
            nullToEmpty(dto.getContentText()),
            nullToEmpty(dto.getContentUrl()),
            nullToEmpty(dto.getDescription()));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is not available");
    }
  }

  static GuardedPaymentResponseDto replayOrConflict(
      GuardedPaymentIdempotency row, GuardedPaymentRequestDto dto) {
    if (!row.getRequestHash().equals(requestHash(dto))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Idempotency key was already used with a different payment");
    }
    try {
      return JSON.readValue(row.getResponseBody(), GuardedPaymentResponseDto.class);
    } catch (JsonProcessingException unreadable) {
      throw new IllegalStateException("Stored guarded payment response is not readable");
    }
  }

  static String toJson(GuardedPaymentResponseDto response) {
    try {
      return JSON.writeValueAsString(response);
    } catch (JsonProcessingException notSerializable) {
      throw new IllegalStateException(
          "Guarded payment response is not serializable: "
              + notSerializable.getOriginalMessage(),
          notSerializable);
    }
  }

  private static String nullToEmpty(Object value) {
    return value == null ? "" : value.toString();
  }

  private static String lowerOrEmpty(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
