package com.guardpay.auth;

import com.guardpay.user.User;

/** Request-scoped authenticated user; never populated from client-supplied input. */
public final class AuthContext {

  private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

  private AuthContext() {}

  public static User currentUser() {
    return CURRENT.get();
  }

  static void set(User user) {
    CURRENT.set(user);
  }

  static void clear() {
    CURRENT.remove();
  }
}
