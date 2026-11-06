package com.guardpay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository users;
  @Mock private AuthSessionRepository sessions;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(users, sessions);
    lenient()
        .when(users.save(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(sessions.save(any(AuthSession.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void registerStoresBcryptHashAndOnlySessionHash() {
    when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());

    AuthService.IssuedSession issued = service.register(" New@Example.com ", "correct-horse", null);

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    verify(users).save(savedUser.capture());
    assertEquals("new@example.com", savedUser.getValue().getEmail());
    assertTrue(new BCryptPasswordEncoder().matches("correct-horse", savedUser.getValue().getPasswordHash()));
    assertNotEquals("correct-horse", savedUser.getValue().getPasswordHash());

    ArgumentCaptor<AuthSession> savedSession = ArgumentCaptor.forClass(AuthSession.class);
    verify(sessions).save(savedSession.capture());
    assertEquals(AuthService.hash(issued.token()), savedSession.getValue().getTokenHash());
    assertNotEquals(issued.token(), savedSession.getValue().getTokenHash());
    assertNotNull(savedSession.getValue().getLastUsedAt());
    Duration idle =
        Duration.between(
            savedSession.getValue().getLastUsedAt(), savedSession.getValue().getExpiresAt());
    Duration absolute =
        Duration.between(
            savedSession.getValue().getLastUsedAt(),
            savedSession.getValue().getAbsoluteExpiresAt());
    assertEquals(AuthService.SESSION_IDLE_TTL, idle);
    assertEquals(AuthService.SESSION_ABSOLUTE_TTL, absolute);
  }

  @Test
  void duplicateRegistrationConflicts() {
    when(users.findByEmailIgnoreCase("new@example.com"))
        .thenReturn(Optional.of(new User()));

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class,
            () -> service.register("new@example.com", "correct-horse", null));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    verify(sessions, never()).save(any());
  }

  @Test
  void loginRejectsWrongPasswordWithoutIssuingSession() {
    User user = new User();
    user.setPasswordHash(new BCryptPasswordEncoder().encode("correct-horse"));
    when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.of(user));

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class,
            () -> service.login("new@example.com", "wrong-password"));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    verify(sessions, never()).save(any());
  }

  @Test
  void loginIssuesFreshOpaqueTokens() {
    User user = new User();
    user.setPasswordHash(new BCryptPasswordEncoder().encode("correct-horse"));
    when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.of(user));

    AuthService.IssuedSession first = service.login("new@example.com", "correct-horse");
    AuthService.IssuedSession second = service.login("new@example.com", "correct-horse");

    assertNotEquals(first.token(), second.token());
    assertTrue(first.token().length() >= 43);
  }

  @Test
  void logoutDeletesByHashOnly() {
    service.logout("opaque-token");

    verify(sessions).deleteByTokenHash(AuthService.hash("opaque-token"));
  }

  @Test
  void shortPasswordsAreRejectedBeforePersistence() {
    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class,
            () -> service.register("new@example.com", "short", null));

    assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    verify(users, never()).save(any());
  }

  @Test
  void rotateRejectsMissingOrUnknownSessions() {
    ResponseStatusException missing =
        assertThrows(ResponseStatusException.class, () -> service.rotate(null));
    assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());

    when(sessions.findActiveByTokenHash(any(), any())).thenReturn(Optional.empty());
    ResponseStatusException unknown =
        assertThrows(ResponseStatusException.class, () -> service.rotate("old-token"));
    assertEquals(HttpStatus.UNAUTHORIZED, unknown.getStatusCode());
    verify(sessions, never()).save(any());
  }

  @Test
  void rotateInvalidatesTheOldSessionAndIssuesANewToken() {
    User user = new User();
    AuthSession existing = new AuthSession();
    existing.setUser(user);
    when(sessions.findActiveByTokenHash(eq(AuthService.hash("old-token")), any()))
        .thenReturn(Optional.of(existing));

    AuthService.IssuedSession issued = service.rotate("old-token");

    verify(sessions).delete(existing);
    verify(sessions).save(any(AuthSession.class));
    assertNotEquals("old-token", issued.token());
  }

  @Test
  void logoutAllDeletesEverySessionForTheCurrentUser() {
    User user = new User();
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    AuthContext.set(user);
    try {
      service.logoutAll();
      verify(sessions).deleteByUserId(userId);
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void revokeSessionRefusesAnotherOwnersSession() {
    User user = new User();
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());
    AuthContext.set(user);
    try {
      ResponseStatusException failure =
          assertThrows(ResponseStatusException.class, () -> service.revokeSession(sessionId));
      assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
      verify(sessions, never()).delete(any());
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void changePasswordRequiresTheCurrentPasswordAndInvalidatesOldSessions() {
    User user = new User();
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    user.setPasswordHash(new BCryptPasswordEncoder().encode("old-password"));
    AuthContext.set(user);
    try {
      ResponseStatusException wrongPassword =
          assertThrows(
              ResponseStatusException.class,
              () -> service.changePassword("wrong-password", "new-password"));
      assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
      verify(sessions, never()).deleteByUserId(any());

      AuthService.IssuedSession issued =
          service.changePassword("old-password", "new-password");

      assertNotNull(issued.token());
      assertTrue(
          new BCryptPasswordEncoder().matches("new-password", user.getPasswordHash()));
      verify(users).save(user);
      verify(sessions).deleteByUserId(userId);
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void listSessionsOmitsIdleOrAbsolutelyExpiredSessions() {
    User user = new User();
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    AuthSession active = new AuthSession();
    active.setLastUsedAt(now.minusMinutes(1));
    active.setExpiresAt(now.plusHours(1));
    active.setAbsoluteExpiresAt(now.plusDays(1));

    AuthSession idleExpired = new AuthSession();
    idleExpired.setLastUsedAt(now.minusHours(1));
    idleExpired.setExpiresAt(now.minusMinutes(1));
    idleExpired.setAbsoluteExpiresAt(now.plusDays(1));

    AuthSession absolutelyExpired = new AuthSession();
    absolutelyExpired.setLastUsedAt(now.minusMinutes(1));
    absolutelyExpired.setExpiresAt(now.plusHours(1));
    absolutelyExpired.setAbsoluteExpiresAt(now.minusMinutes(1));

    when(sessions.findAllByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of(active, idleExpired, absolutelyExpired));
    AuthContext.set(user);
    try {
      List<AuthService.SessionView> visible = service.listSessions();
      assertEquals(1, visible.size());
      assertEquals(active.getExpiresAt(), visible.get(0).expiresAt());
    } finally {
      AuthContext.clear();
    }
  }
}
