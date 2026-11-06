package com.guardpay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

  @Mock private AuthSessionRepository sessions;
  @Mock private FilterChain chain;

  private AuthFilter filter;

  @BeforeEach
  void setUp() {
    filter = new AuthFilter(sessions);
  }

  @Test
  void protectedRequestWithoutCookieIsRejectedAndContextIsClean() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    verify(chain, never()).doFilter(any(), any());
    assertNull(AuthContext.currentUser());
  }

  @Test
  void publicHealthRequestDoesNotRequireCookie() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertEquals(200, response.getStatus());
  }

  @Test
  void validSessionCookiePopulatesContextForTheDownstreamRequestOnly() throws Exception {
    User user = new User();
    AuthSession session = new AuthSession();
    session.setUser(user);
    when(sessions.findActiveByTokenHash(eq(AuthService.hash("opaque")), any()))
        .thenReturn(Optional.of(session));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
    request.setCookies(new Cookie(AuthService.COOKIE_NAME, "opaque"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<User> duringChain = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              duringChain.set(AuthContext.currentUser());
              return null;
            })
        .when(chain)
        .doFilter(request, response);

    filter.doFilter(request, response, chain);

    assertSame(user, duringChain.get());
    assertNull(AuthContext.currentUser());
    assertEquals(200, response.getStatus());
  }

  @Test
  void expiredOrUnknownSessionBehavesLikeNoSession() throws Exception {
    when(sessions.findActiveByTokenHash(any(), any(OffsetDateTime.class)))
        .thenReturn(Optional.empty());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
    request.setCookies(new Cookie(AuthService.COOKIE_NAME, "opaque"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void validSessionSlidesIdleExpiryWithoutExceedingAbsoluteExpiry() throws Exception {
    User user = new User();
    AuthSession session = new AuthSession();
    UUID sessionId = UUID.randomUUID();
    ReflectionTestUtils.setField(session, "id", sessionId);
    session.setUser(user);
    when(sessions.findActiveByTokenHash(eq(AuthService.hash("opaque")), any()))
        .thenReturn(Optional.of(session));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
    request.setCookies(new Cookie(AuthService.COOKIE_NAME, "opaque"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    ArgumentCaptor<OffsetDateTime> now = ArgumentCaptor.forClass(OffsetDateTime.class);
    ArgumentCaptor<OffsetDateTime> slidingExpiry =
        ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(sessions).touch(eq(sessionId), now.capture(), slidingExpiry.capture());
    assertEquals(AuthService.SESSION_IDLE_TTL, java.time.Duration.between(now.getValue(), slidingExpiry.getValue()));
    verify(chain).doFilter(request, response);
  }

  @Test
  void sessionManagementEndpointsAreProtectedButCsrfBootstrapIsPublic() throws Exception {
    MockHttpServletRequest sessionsRequest =
        new MockHttpServletRequest("GET", "/api/auth/sessions");
    MockHttpServletResponse sessionsResponse = new MockHttpServletResponse();
    filter.doFilter(sessionsRequest, sessionsResponse, chain);
    assertEquals(401, sessionsResponse.getStatus());

    MockHttpServletRequest csrfRequest = new MockHttpServletRequest("GET", "/api/auth/csrf");
    MockHttpServletResponse csrfResponse = new MockHttpServletResponse();
    filter.doFilter(csrfRequest, csrfResponse, chain);
    verify(chain).doFilter(csrfRequest, csrfResponse);
  }

  @Test
  void deviceSignalIntakeIsPublicBecauseItUsesADeviceCredential() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/device-signals/call-state");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}
