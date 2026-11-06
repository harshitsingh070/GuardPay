package com.guardpay.auth;

import com.guardpay.common.security.TokenHasher;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Registration, login, opaque session issuance, and session lifecycle. */
@Service
public class AuthService {

  public static final String COOKIE_NAME = "guardpay_session";

  public static final Duration SESSION_IDLE_TTL = Duration.ofHours(24);
  public static final Duration SESSION_ABSOLUTE_TTL = Duration.ofDays(7);
  public static final int SESSION_TTL_DAYS = (int) SESSION_ABSOLUTE_TTL.toDays();

  private final UserRepository users;
  private final AuthSessionRepository sessions;
  private final LoginRateLimiter rateLimiter;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  /** Test/tooling constructor with the default protection thresholds. */
  public AuthService(UserRepository users, AuthSessionRepository sessions) {
    this(
        users,
        sessions,
        new LoginRateLimiter(
            5,
            20,
            Duration.ofMinutes(15),
            Duration.ofMinutes(15),
            java.time.Clock.systemUTC()));
  }

  @Autowired
  public AuthService(
      UserRepository users, AuthSessionRepository sessions, LoginRateLimiter rateLimiter) {
    this.users = users;
    this.sessions = sessions;
    this.rateLimiter = rateLimiter;
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
    return login(email, password, null, null);
  }

  @Transactional
  public IssuedSession login(String email, String password, String existingToken) {
    return login(email, password, existingToken, null);
  }

  @Transactional
  public IssuedSession login(
      String email, String password, String existingToken, String clientIp) {
    String normalized = normalizeEmail(email);
    rateLimiter.checkAllowed(normalized, clientIp);
    User user =
        users
            .findByEmailIgnoreCase(normalized)
            .filter(candidate -> candidate.getPasswordHash() != null)
            .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
            .orElseGet(
                () -> {
                  rateLimiter.recordFailure(normalized, clientIp);
                  throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });
    rateLimiter.reset(normalized, clientIp);
    deleteToken(existingToken);
    return issueSession(user);
  }

  @Transactional
  public void logout(String token) {
    deleteToken(token);
  }

  @Transactional
  public void logoutAll() {
    sessions.deleteByUserId(currentUser().getId());
  }

  public User currentUser() {
    return AuthContext.currentUser();
  }

  /** Invalidates the supplied session and issues a fresh one for the same account. */
  @Transactional
  public IssuedSession rotate(String currentToken) {
    if (currentToken == null || currentToken.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    AuthSession session =
        sessions
            .findActiveByTokenHash(hash(currentToken), OffsetDateTime.now(ZoneOffset.UTC))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
    sessions.delete(session);
    return issueSession(session.getUser());
  }

  @Transactional(readOnly = true)
  public List<SessionView> listSessions() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return sessions.findAllByUserIdOrderByCreatedAtDesc(currentUser().getId()).stream()
        .filter(
            session ->
                session.getExpiresAt().isAfter(now)
                    && session.getAbsoluteExpiresAt().isAfter(now))
        .map(
            session ->
                new SessionView(
                    session.getId(),
                    session.getCreatedAt(),
                    session.getLastUsedAt(),
                    session.getExpiresAt(),
                    session.getAbsoluteExpiresAt()))
        .toList();
  }

  @Transactional
  public void revokeSession(UUID sessionId) {
    AuthSession session =
        sessions
            .findByIdAndUserId(sessionId, currentUser().getId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    sessions.delete(session);
  }

  /** Changes the password, invalidates every old session, and rotates this one. */
  @Transactional
  public IssuedSession changePassword(String currentPassword, String newPassword) {
    User user = currentUser();
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
    }
    validatePassword(newPassword);
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    users.save(user);
    sessions.deleteByUserId(user.getId());
    return issueSession(user);
  }

  private IssuedSession issueSession(User user) {
    String token = TokenHasher.randomToken();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    AuthSession session = new AuthSession();
    session.setUser(user);
    session.setTokenHash(hash(token));
    session.setLastUsedAt(now);
    session.setExpiresAt(now.plus(SESSION_IDLE_TTL));
    session.setAbsoluteExpiresAt(now.plus(SESSION_ABSOLUTE_TTL));
    sessions.save(session);
    return new IssuedSession(token, user);
  }

  private void deleteToken(String token) {
    if (token != null && !token.isBlank()) {
      sessions.deleteByTokenHash(hash(token));
    }
  }

  private static void validatePassword(String password) {
    if (password == null || password.length() < 8 || password.length() > 72) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters");
    }
  }

  private static String normalizeEmail(String email) {
    String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty() || normalized.length() > 254 || !normalized.contains("@")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
    }
    return normalized;
  }

  public static String hash(String token) {
    return TokenHasher.sha256Hex(token);
  }

  public record IssuedSession(String token, User user) {}

  public record SessionView(
      UUID id,
      OffsetDateTime createdAt,
      OffsetDateTime lastUsedAt,
      OffsetDateTime expiresAt,
      OffsetDateTime absoluteExpiresAt) {}
}
