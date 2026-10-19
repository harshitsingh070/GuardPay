package com.guardpay.risk;

import com.guardpay.risk.dto.RiskEvaluationRequestDto;
import com.guardpay.risk.dto.RiskEvaluationResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risk evaluation endpoint (development stage — no authentication yet; the
 * auth milestone will protect it).
 *
 * <p>Thin by design: validates, delegates to {@link RiskEvaluationService},
 * and returns HTTP 200. No risk calculations live here.
 */
@RestController
@RequestMapping("/api/risk")
public class RiskController {

  private final RiskEvaluationService service;

  public RiskController(RiskEvaluationService service) {
    this.service = service;
  }

  @PostMapping("/evaluate")
  public ResponseEntity<RiskEvaluationResponseDto> evaluate(
      @Valid @RequestBody RiskEvaluationRequestDto request) {
    return ResponseEntity.ok(service.evaluate(request));
  }
}
