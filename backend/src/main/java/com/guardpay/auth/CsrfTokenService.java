package com.guardpay.auth;

import com.guardpay.common.security.TokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stateless CSRF token bound to the current session (or to anonymous clients).
 * The token is sent in a response body, not a readable cookie, and must be
 * echoed in a custom header on every unsafe request.
 */
@Service
public class CsrfTokenService {

  public static final String HEADER = "X-CSRF-Token";
  public static final String ANONYMOUS_SCOPE = "anonymous";

  private final SecretKeySpec key;

  public CsrfTokenService(@Value("${guardpay.auth.csrf-secret:}") String configuredSecret) {
    byte[] secret =
        configuredSecret == null || configuredSecret.isBlank()
            ? TokenHasher.randomToken().getBytes(StandardCharsets.UTF_8)
            : configuredSecret.getBytes(StandardCharsets.UTF_8);
    this.key = new SecretKeySpec(sha256(secret), "HmacSHA256");
  }

  public static String scopeFor(String sessionToken) {
    return sessionToken == null || sessionToken.isBlank()
        ? ANONYMOUS_SCOPE
        : AuthService.hash(sessionToken);
  }

  public String issue(String scope) {
    String nonce = TokenHasher.randomToken();
    return nonce + "." + sign(nonce, scope == null ? ANONYMOUS_SCOPE : scope);
  }

  public boolean isValid(String token, String scope) {
    if (token == null || token.isBlank() || token.length() > 512) {
      return false;
    }
    int split = token.lastIndexOf('.');
    if (split <= 0 || split == token.length() - 1) {
      return false;
    }
    String nonce = token.substring(0, split);
    String supplied = token.substring(split + 1);
    String expected = sign(nonce, scope == null ? ANONYMOUS_SCOPE : scope);
    return MessageDigest.isEqual(
        supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }

  private String sign(String nonce, String scope) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      byte[] digest = mac.doFinal((nonce + "." + scope).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception impossible) {
      throw new IllegalStateException("HmacSHA256 is required", impossible);
    }
  }

  private static byte[] sha256(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (Exception impossible) {
      throw new IllegalStateException("SHA-256 is required", impossible);
    }
  }
}
