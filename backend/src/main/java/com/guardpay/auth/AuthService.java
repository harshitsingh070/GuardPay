package com.guardpay.auth;

import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Registration, login, opaque session issuance, and logout. */
@Service
public class AuthService {

  public static final String COOKIE_NAME = "guardpay_session";

  static final int SESSION_TTL_DAYS = 7;
  private static final Duration SESSION_TTL = Duration.ofDays(SESSION_TTL_DAYS);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final AuthSessionRepository sessions;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public AuthService(UserRepository users, AuthSessionRepository sessions) {
    this.users = users;
    this.sessions = sessions;
  }

  @Transactional
  public IssuedSession register(String email, String password, String displayName) {
    String normalized = normalizeEmail(email);
    validatePassword(password);
    if (users.findByEmailIgnoreCase(normalized).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email exists");
    }
    User user = new User();
    user.setExternalUserId("user-" + UUID.randomUUID());
    user.setEmail(normalized);
    user.setDisplayName(
        displayName == null || displayName.isBlank()
            ? normalized.substring(0, normalized.indexOf('@'))
            : displayName.trim());
    user.setCurrency("USD");
    user.setPasswordHash(passwordEncoder.encode(password));
    users.save(user);
    return issueSession(user);
  }

  @Transactional
  public IssuedSession login(String email, String password) {
    User user =
        users
            .findByEmailIgnoreCase(normalizeEmail(email))
            .filter(candidate -> candidate.getPasswordHash() != null)
            .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    return issueSession(user);
  }

  @Transactional
  public void logout(String token) {
    if (token != null && !token.isBlank()) {
      sessions.deleteByTokenHash(hash(token));
    }
  }

  public User currentUser() {
    return AuthContext.currentUser();
  }

  private IssuedSession issueSession(User user) {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    AuthSession session = new AuthSession();
    session.setUser(user);
    session.setTokenHash(hash(token));
    session.setExpiresAt(now.plus(SESSION_TTL));
    sessions.save(session);
    return new IssuedSession(token, user);
  }

  private static void validatePassword(String password) {
    if (password == null || password.length() < 8 || password.length() > 72) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters");
    }
  }

  private static String normalizeEmail(String email) {
    String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty() || normalized.length() > 254) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
    }
    return normalized;
  }

  public static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required", impossible);
    }
  }

  public record IssuedSession(String token, User user) {}
}
