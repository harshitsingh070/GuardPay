package com.guardpay.user;

import com.guardpay.auth.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the authenticated user, or the single demo account when auth is
 * disabled for unit/integration tests and local fixture tooling.
 */
@Service
public class DemoUserService {

  public static final String EXTERNAL_USER_ID = "guardpay-demo";

  private final UserRepository users;
  private final boolean authEnabled;

  public DemoUserService(UserRepository users) {
    this(users, false);
  }

  @Autowired
  public DemoUserService(
      UserRepository users, @Value("${guardpay.auth.enabled:true}") boolean authEnabled) {
    this.users = users;
    this.authEnabled = authEnabled;
  }

  @Transactional(readOnly = true)
  public User currentUser() {
    if (authEnabled) {
      User authenticated = AuthContext.currentUser();
      if (authenticated == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
      }
      return authenticated;
    }
    return users
        .findByExternalUserId(EXTERNAL_USER_ID)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Demo user is missing; apply database migrations"));
  }
}
