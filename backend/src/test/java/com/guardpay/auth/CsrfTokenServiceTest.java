package com.guardpay.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CsrfTokenServiceTest {

  private final CsrfTokenService service = new CsrfTokenService("test-csrf-secret");

  @Test
  void acceptsTokenForTheSameSessionScope() {
    String scope = CsrfTokenService.scopeFor("opaque-session-token");
    String token = service.issue(scope);

    assertTrue(service.isValid(token, scope));
  }

  @Test
  void rejectsTokenIssuedForAnotherSession() {
    String token = service.issue(CsrfTokenService.scopeFor("session-a"));

    assertFalse(service.isValid(token, CsrfTokenService.scopeFor("session-b")));
    assertFalse(service.isValid(token, CsrfTokenService.ANONYMOUS_SCOPE));
  }

  @Test
  void rejectsMalformedTokens() {
    assertFalse(service.isValid(null, CsrfTokenService.ANONYMOUS_SCOPE));
    assertFalse(service.isValid("", CsrfTokenService.ANONYMOUS_SCOPE));
    assertFalse(service.isValid("no-separator", CsrfTokenService.ANONYMOUS_SCOPE));
    assertFalse(service.isValid(".signature-only", CsrfTokenService.ANONYMOUS_SCOPE));
    assertFalse(service.isValid("nonce.", CsrfTokenService.ANONYMOUS_SCOPE));
  }

  @Test
  void anonymousTokenIsValidOnlyForAnonymousScope() {
    String token = service.issue(CsrfTokenService.ANONYMOUS_SCOPE);

    assertTrue(service.isValid(token, CsrfTokenService.ANONYMOUS_SCOPE));
    assertFalse(service.isValid(token, CsrfTokenService.scopeFor("opaque-session-token")));
  }
}
