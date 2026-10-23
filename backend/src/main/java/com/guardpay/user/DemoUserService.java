package com.guardpay.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the single demo account used by this prototype.
 *
 * <p>Authentication and per-user ownership were removed deliberately. Every
 * API caller acts as this server-side account; clients can never supply or
 * select a user identity. The account is created by Flyway so a missing row is
 * a deployment error, not something to guess around.
 */
@Service
public class DemoUserService {

  public static final String EXTERNAL_USER_ID = "guardpay-demo";

  private final UserRepository users;

  public DemoUserService(UserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public User currentUser() {
    return users
        .findByExternalUserId(EXTERNAL_USER_ID)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Demo user is missing; apply database migrations"));
  }
}
