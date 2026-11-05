package com.guardpay.dashboard;

import com.guardpay.dashboard.dto.DashboardSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Live, read-only dashboard aggregates for the authenticated workspace. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public ResponseEntity<DashboardSummaryDto> summary() {
    return ResponseEntity.ok(service.summary());
  }
}

