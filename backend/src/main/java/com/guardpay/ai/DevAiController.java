package com.guardpay.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loopback-only demo switch for the AI evidence service. Removing AI proves
 * the deterministic engine still decides and the dashboard reports the
 * degraded mode. Disabled for any non-local caller.
 */
@RestController
@RequestMapping("/api/dev/ai")
public class DevAiController {

  private final AiRuntimeControl runtime;

  public DevAiController(AiRuntimeControl runtime) {
    this.runtime = runtime;
  }

  @GetMapping
  public ResponseEntity<AiStatusDto> status(HttpServletRequest request) {
    if (!isLoopback(request.getRemoteAddr())) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(new AiStatusDto(runtime.isEnabled()));
  }

  @PostMapping
  public ResponseEntity<AiStatusDto> set(
      @RequestBody AiStatusDto body, HttpServletRequest request) {
    if (!isLoopback(request.getRemoteAddr())) {
      return ResponseEntity.notFound().build();
    }
    runtime.setEnabled(body.enabled());
    return ResponseEntity.ok(new AiStatusDto(runtime.isEnabled()));
  }

  private static boolean isLoopback(String remoteAddress) {
    return "127.0.0.1".equals(remoteAddress)
        || "0:0:0:0:0:0:0:1".equals(remoteAddress)
        || "::1".equals(remoteAddress);
  }

  public record AiStatusDto(boolean enabled) {}
}
