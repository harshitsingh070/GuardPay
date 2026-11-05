package com.guardpay.alert;

import com.guardpay.alert.dto.AlertDto;
import com.guardpay.user.DemoUserService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Read-only alert feed plus the browser SSE stream. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

  private static final int SNAPSHOT_SIZE = 10;

  private final AlertService alerts;
  private final AlertStreamService stream;
  private final DemoUserService demoUser;

  public AlertController(
      AlertService alerts, AlertStreamService stream, DemoUserService demoUser) {
    this.alerts = alerts;
    this.stream = stream;
    this.demoUser = demoUser;
  }

  @GetMapping
  public List<AlertDto> recent(@RequestParam(name = "limit", defaultValue = "20") int limit) {
    return alerts.recent(limit);
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    return stream.subscribe(demoUser.currentUser().getId(), alerts.recent(SNAPSHOT_SIZE));
  }
}
