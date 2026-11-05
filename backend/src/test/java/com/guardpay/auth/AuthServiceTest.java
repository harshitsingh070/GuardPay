package com.guardpay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    assertNotNull(savedSession.getValue().getExpiresAt());
    assertTrue(savedSession.getValue().getExpiresAt().isAfter(java.time.OffsetDateTime.now()));
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
}
