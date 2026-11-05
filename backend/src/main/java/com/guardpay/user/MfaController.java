package com.guardpay.user;

import com.guardpay.user.MfaService.TotpEnrollment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-account TOTP setup. The live payment flow only ever asks for a code;
 * it never returns the authenticator secret.
 */
@RestController
@RequestMapping("/api/mfa/totp")
public class MfaController {

  private final MfaService service;

  public MfaController(MfaService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<TotpEnrollment> status() {
    return ResponseEntity.ok(service.status());
  }

  @PostMapping("/enroll")
  public ResponseEntity<TotpEnrollment> enroll() {
    return ResponseEntity.ok(service.enroll());
  }

  @DeleteMapping
  public ResponseEntity<TotpEnrollment> disable() {
    return ResponseEntity.ok(service.disable());
  }
}
