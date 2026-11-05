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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
}
