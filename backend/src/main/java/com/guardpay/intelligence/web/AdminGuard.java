package com.guardpay.intelligence.web;

import com.guardpay.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Server-side admin authorization for the intelligence review console. */
@Component
public class AdminGuard {

  public void requireAdmin(User user) {
    if (user == null || !user.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
  }
}
