package com.guardpay.risk.signal;

import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Automatic risk evaluation (authenticated).
 * Accepts payment context only; signals are backend-derived. The manual
 * {@code POST /api/risk/evaluate} contract is untouched.
 */
@RestController
@RequestMapping("/api/risk")
public class AutomaticRiskController {

  private final AutomaticRiskEvaluationService service;

  public AutomaticRiskController(AutomaticRiskEvaluationService service) {
    this.service = service;
  }

  @PostMapping("/evaluate-automatic")
  public ResponseEntity<AutomaticEvaluationResponseDto> evaluateAutomatic(
      @Valid @RequestBody AutomaticEvaluationRequestDto request) {
    return ResponseEntity.ok(service.evaluate(request));
  }
}


