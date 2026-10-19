package com.guardpay.common.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness probe for the GuardPay backend.
 *
 * <p>Intentionally dependency-free (no database access) so load balancers,
 * Docker health checks, and the frontend can verify the service is up even
 * when downstream systems are unavailable.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

  @GetMapping
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "UP", "service", "guardpay-backend"));
  }
}
