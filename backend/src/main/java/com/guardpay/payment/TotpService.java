package com.guardpay.payment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Minimal RFC 6238 TOTP (SHA-1, 6 digits, 30-second step).
 *
 * <p>Implemented locally to keep the demo dependency-free. Verification
 * accepts the current step plus one step on either side for clock drift.
 * Secrets are base32 (RFC 4648) and never logged.
 */
@Service
public class TotpService {

  static final int DIGITS = 6;
  static final int STEP_SECONDS = 30;
  private static final int WINDOW_STEPS = 1;
  private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Generates a new 160-bit base32 secret. */
  public String generateSecret() {
    byte[] bytes = new byte[20];
    RANDOM.nextBytes(bytes);
    return base32Encode(bytes);
  }

  public String code(String secret, Instant instant) {
    long counter = instant.getEpochSecond() / STEP_SECONDS;
    return hotp(secret, counter);
  }

  public boolean verify(String secret, String submitted, Instant instant) {
    if (secret == null || secret.isBlank() || submitted == null) {
      return false;
    }
    String normalized = submitted.trim().replace(" ", "");
    if (normalized.length() != DIGITS) {
      return false;
    }
    long counter = instant.getEpochSecond() / STEP_SECONDS;
    for (int offset = -WINDOW_STEPS; offset <= WINDOW_STEPS; offset++) {
      String expected = hotp(secret, counter + offset);
      if (MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          normalized.getBytes(StandardCharsets.US_ASCII))) {
        return true;
      }
    }
    return false;
  }

  /** otpauth URI for authenticator apps (Google Authenticator, Authy, 1Password). */
  public String otpauthUri(String issuer, String account, String secret) {
    String label = urlEncode(issuer) + ":" + urlEncode(account);
    return "otpauth://totp/"
        + label
        + "?secret="
        + secret
        + "&issuer="
        + urlEncode(issuer)
        + "&algorithm=SHA1&digits="
        + DIGITS
        + "&period="
        + STEP_SECONDS;
  }

  private static String hotp(String secret, long counter) {
    byte[] key = base32Decode(secret);
    byte[] message = ByteBuffer.allocate(8).putLong(counter).array();
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(message);
      int offset = hash[hash.length - 1] & 0x0f;
      int binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + 3] & 0xff);
      int otp = binary % (int) Math.pow(10, DIGITS);
      return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("HmacSHA1 is required for TOTP", impossible);
    }
  }

  static String base32Encode(byte[] data) {
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bitsLeft = 0;
    for (byte value : data) {
      buffer = (buffer << 8) | (value & 0xff);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
    }
    return out.toString();
  }

  static byte[] base32Decode(String secret) {
    String normalized = secret.trim().replace("=", "").toUpperCase(Locale.ROOT);
    int buffer = 0;
    int bitsLeft = 0;
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    for (int i = 0; i < normalized.length(); i++) {
      int value = BASE32_ALPHABET.indexOf(normalized.charAt(i));
      if (value < 0) {
        throw new IllegalArgumentException("Invalid base32 secret");
      }
      buffer = (buffer << 5) | value;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out.write((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    return out.toByteArray();
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
        .replace("+", "%20");
  }
}
