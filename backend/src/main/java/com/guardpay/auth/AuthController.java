package com.guardpay.auth;

import com.guardpay.common.net.NetworkContextResolver;
import com.guardpay.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService auth;
  private final CsrfTokenService csrf;
  private final NetworkContextResolver network;
  private final boolean secureCookie;
  private final String sameSite;

  public AuthController(
      AuthService auth,
      CsrfTokenService csrf,
      NetworkContextResolver network,
      @Value("${guardpay.auth.cookie-secure:false}") boolean secureCookie,
      @Value("${guardpay.auth.cookie-same-site:Lax}") String sameSite) {
    this.auth = auth;
    this.csrf = csrf;
    this.network = network;
    this.secureCookie = secureCookie;
    this.sameSite = normalizeSameSite(sameSite, secureCookie);
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthService.IssuedSession issued =
        auth.register(request.email(), request.password(), request.displayName());
    return withSession(issued);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request,
      @CookieValue(name = AuthService.COOKIE_NAME, required = false) String existingToken,
      HttpServletRequest httpRequest) {
    return withSession(
        auth.login(
            request.email(),
            request.password(),
            existingToken,
            network.resolve(httpRequest).clientIp()));
  }

  @PostMapping("/logout")
  public ResponseEntity<AuthResponse> logout(
      @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
    auth.logout(token);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
        .body(anonymousResponse());
  }

  @PostMapping("/logout-all")
  public ResponseEntity<AuthResponse> logoutAll(
      @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
    auth.logoutAll();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
        .body(anonymousResponse());
  }

  @GetMapping("/me")
  public AuthResponse me(
      @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
    User user = AuthContext.currentUser();
    return new AuthResponse(
        user != null,
        user == null ? null : UserView.from(user),
        csrf.issue(CsrfTokenService.scopeFor(token)));
  }

  @GetMapping("/csrf")
  public CsrfResponse csrf(
      @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
    return new CsrfResponse(csrf.issue(CsrfTokenService.scopeFor(token)));
  }

  @GetMapping("/sessions")
  public List<AuthService.SessionView> sessions() {
    return auth.listSessions();
  }

  @DeleteMapping("/sessions/{sessionId}")
  public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId) {
    auth.revokeSession(sessionId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/sessions/rotate")
  public ResponseEntity<AuthResponse> rotate(
      @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
    return withSession(auth.rotate(token));
  }

  @PostMapping("/password")
  public ResponseEntity<AuthResponse> changePassword(
      @Valid @RequestBody PasswordChangeRequest request) {
    return withSession(auth.changePassword(request.currentPassword(), request.newPassword()));
  }

  private ResponseEntity<AuthResponse> withSession(AuthService.IssuedSession issued) {
    ResponseCookie cookie =
        ResponseCookie.from(AuthService.COOKIE_NAME, issued.token())
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite(sameSite)
            .path("/")
            .maxAge(AuthService.SESSION_ABSOLUTE_TTL)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(
            new AuthResponse(
                true,
                UserView.from(issued.user()),
                csrf.issue(CsrfTokenService.scopeFor(issued.token()))));
  }

  private ResponseCookie clearedCookie() {
    return ResponseCookie.from(AuthService.COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite(sameSite)
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  private AuthResponse anonymousResponse() {
    return new AuthResponse(
        false, null, csrf.issue(CsrfTokenService.ANONYMOUS_SCOPE));
  }

  private static String normalizeSameSite(String value, boolean secure) {
    String normalized =
        value == null || value.isBlank() ? "Lax" : value.trim().toLowerCase(Locale.ROOT);
    String canonical =
        switch (normalized) {
          case "strict" -> "Strict";
          case "none" -> "None";
          case "lax" -> "Lax";
          default -> throw new IllegalStateException(
              "guardpay.auth.cookie-same-site must be Lax, Strict, or None");
        };
    if ("None".equals(canonical) && !secure) {
      throw new IllegalStateException("SameSite=None requires guardpay.auth.cookie-secure=true");
    }
    return canonical;
  }

  public record LoginRequest(
      @NotBlank @Email String email, @NotBlank String password) {}

  public record RegisterRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @Size(max = 120) String displayName) {}

  public record PasswordChangeRequest(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record CsrfResponse(String csrfToken) {}

  public record AuthResponse(boolean authenticated, UserView user, String csrfToken) {}

  public record UserView(UUID id, String email, String displayName, boolean admin) {
    static UserView from(User user) {
      return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.isAdmin());
    }
  }
}
