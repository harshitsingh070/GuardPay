package com.guardpay.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Shared handling for opaque bearer/session/device credentials. */
public final class TokenHasher {

  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenHasher() {}

  /** 256-bit URL-safe token with no padding. */
  public static String randomToken() {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  public static String sha256Hex(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required", impossible);
    }
  }
}
