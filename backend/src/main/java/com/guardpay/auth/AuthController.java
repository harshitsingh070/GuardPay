package com.guardpay.auth;

import com.guardpay.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService auth;
  private final boolean secureCookie;

  public AuthController(
      AuthService auth, @Value("${guardpay.auth.cookie-secure:false}") boolean secureCookie) {
    this.auth = auth;
    this.secureCookie = secureCookie;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthService.IssuedSession issued =
        auth.register(request.email(), request.password(), request.displayName());
    return withSession(issued);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return withSession(auth.login(request.email(), request.password()));
  }

  @PostMapping("/logout")
  public ResponseEntity<AuthResponse> logout(
      @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
    auth.logout(token);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
        .body(new AuthResponse(false, null));
  }

  @GetMapping("/me")
  public AuthResponse me() {
    User user = AuthContext.currentUser();
    return new AuthResponse(user != null, user == null ? null : UserView.from(user));
  }

  private ResponseEntity<AuthResponse> withSession(AuthService.IssuedSession issued) {
    ResponseCookie cookie =
        ResponseCookie.from(AuthService.COOKIE_NAME, issued.token())
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(AuthService.SESSION_TTL_DAYS))
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(new AuthResponse(true, UserView.from(issued.user())));
  }

  private ResponseCookie clearedCookie() {
    return ResponseCookie.from(AuthService.COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  public record LoginRequest(
      @NotBlank @Email String email, @NotBlank String password) {}

  public record RegisterRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @Size(max = 120) String displayName) {}

  public record AuthResponse(boolean authenticated, UserView user) {}

  public record UserView(UUID id, String email, String displayName) {
    static UserView from(User user) {
      return new UserView(user.getId(), user.getEmail(), user.getDisplayName());
    }
  }
}
