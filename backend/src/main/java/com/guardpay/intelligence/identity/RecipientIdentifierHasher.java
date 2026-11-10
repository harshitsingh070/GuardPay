package com.guardpay.intelligence.identity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA-256 hashing for recipient identifiers.
 *
 * <p>Raw identifiers are never persisted or logged. Each configured key
 * version can be used for lookups so a key rotation does not lose existing
 * matches; new writes always use the active version. A blank secret makes the
 * component unconfigured and callers fail safe (intelligence becomes
 * {@code UNAVAILABLE}) instead of falling back to a reversible encoding.
 */
public final class RecipientIdentifierHasher {

  public static final String ALGORITHM = "HmacSHA256";

  private final Map<Integer, byte[]> keys;
  private final int activeKeyVersion;

  public RecipientIdentifierHasher(Map<String, String> configuredKeys, int activeKeyVersion) {
    Map<Integer, byte[]> parsed = new LinkedHashMap<>();
    if (configuredKeys != null) {
      for (Map.Entry<String, String> entry : configuredKeys.entrySet()) {
        String secret = entry.getValue();
        if (entry.getKey() == null || secret == null || secret.isBlank()) {
          continue;
        }
        try {
          int version = Integer.parseInt(entry.getKey().trim());
          parsed.put(version, secret.getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException notAVersion) {
          // An invalid key version is ignored rather than silently hashing
          // with an unexpected key.
        }
      }
    }
    this.keys = Map.copyOf(parsed);
    this.activeKeyVersion = activeKeyVersion;
  }

  public boolean isConfigured() {
    return keys.containsKey(activeKeyVersion);
  }

  public int activeKeyVersion() {
    return activeKeyVersion;
  }

  public HmacDigest activeDigest(CanonicalIdentifier identifier) {
    byte[] key = keys.get(activeKeyVersion);
    if (key == null) {
      throw new IllegalStateException("Recipient intelligence HMAC key is not configured");
    }
    return new HmacDigest(hmac(key, identifier), activeKeyVersion);
  }

  /** Digests for every configured key version, newest first, for lookups. */
  public List<HmacDigest> digests(CanonicalIdentifier identifier) {
    if (!isConfigured()) {
      return List.of();
    }
    List<HmacDigest> result = new ArrayList<>(keys.size());
    byte[] active = keys.get(activeKeyVersion);
    if (active != null) {
      result.add(new HmacDigest(hmac(active, identifier), activeKeyVersion));
    }
    for (Map.Entry<Integer, byte[]> entry : keys.entrySet()) {
      if (entry.getKey() != activeKeyVersion) {
        result.add(new HmacDigest(hmac(entry.getValue(), identifier), entry.getKey()));
      }
    }
    return List.copyOf(result);
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static String hmac(byte[] key, CanonicalIdentifier identifier) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key, ALGORITHM));
      String input =
          identifier.type().name()
              + "|"
              + identifier.normalizationVersion()
              + "|"
              + identifier.canonicalValue();
      return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", failure);
    }
  }

  /** One digest plus the key version that produced it. */
  public record HmacDigest(String digest, int keyVersion) {
    public HmacDigest {
      if (digest == null || digest.length() != 64) {
        throw new IllegalArgumentException("HMAC digest must be 64 hex characters");
      }
    }
  }
}
