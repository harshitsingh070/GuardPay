package com.guardpay.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Small in-memory brute-force guard. It limits both a single email/IP pair and
 * a single source IP; a successful login clears the pair counter.
 */
@Service
public class LoginRateLimiter {

  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final Duration LOCKOUT = Duration.ofMinutes(15);
  private static final int CREDENTIAL_MAX = 5;
  private static final int IP_MAX = 20;

  private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
  private final Clock clock;
  private final int credentialMax;
  private final int ipMax;
  private final Duration window;
  private final Duration lockout;

  @Autowired
  public LoginRateLimiter(
      @Value("${guardpay.auth.login-max-attempts:5}") int credentialMax,
      @Value("${guardpay.auth.login-ip-max-attempts:20}") int ipMax,
      @Value("${guardpay.auth.login-window-minutes:15}") long windowMinutes,
      @Value("${guardpay.auth.login-lockout-minutes:15}") long lockoutMinutes) {
    this(credentialMax, ipMax, Duration.ofMinutes(windowMinutes), Duration.ofMinutes(lockoutMinutes),
        Clock.systemUTC());
  }

  LoginRateLimiter(
      int credentialMax, int ipMax, Duration window, Duration lockout, Clock clock) {
    this.credentialMax = Math.max(1, credentialMax);
    this.ipMax = Math.max(1, ipMax);
    this.window = window;
    this.lockout = lockout;
    this.clock = clock;
  }

  public void checkAllowed(String email, String clientIp) {
    Instant now = clock.instant();
    requireNotBlocked(credentialKey(email, clientIp), now);
    requireNotBlocked(ipKey(clientIp), now);
  }

  public void recordFailure(String email, String clientIp) {
    increment(credentialKey(email, clientIp), credentialMax);
    increment(ipKey(clientIp), ipMax);
  }

  public void reset(String email, String clientIp) {
    attempts.remove(credentialKey(email, clientIp));
  }

  private void requireNotBlocked(String key, Instant now) {
    Attempt attempt = attempts.get(key);
    if (attempt != null && attempt.blockedUntil != null && attempt.blockedUntil.isAfter(now)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
    }
  }

  private void increment(String key, int threshold) {
    Instant now = clock.instant();
    attempts.compute(
        key,
        (ignored, existing) -> {
          Attempt attempt =
              existing == null || existing.windowStartedAt.plus(window).isBefore(now)
                  ? new Attempt(now, 0, null)
                  : existing;
          if (attempt.blockedUntil != null && attempt.blockedUntil.isAfter(now)) {
            return attempt;
          }
          int count = attempt.count + 1;
          Instant blockedUntil = count >= threshold ? now.plus(lockout) : null;
          return new Attempt(attempt.windowStartedAt, count, blockedUntil);
        });
  }

  private static String credentialKey(String email, String clientIp) {
    String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    return "credential:" + normalized + "|" + ipKey(clientIp);
  }

  private static String ipKey(String clientIp) {
    return "ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim());
  }

  private record Attempt(Instant windowStartedAt, int count, Instant blockedUntil) {}
}
