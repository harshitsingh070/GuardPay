package com.guardpay.risk.signal;

import com.guardpay.common.net.NetworkContext;
import com.guardpay.common.net.NetworkContextResolver;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import jakarta.servlet.http.HttpServletRequest;
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
  private final NetworkContextResolver network;

  public AutomaticRiskController(
      AutomaticRiskEvaluationService service, NetworkContextResolver network) {
    this.service = service;
    this.network = network;
  }

  @PostMapping("/evaluate-automatic")
  public ResponseEntity<AutomaticEvaluationResponseDto> evaluateAutomatic(
      @Valid @RequestBody AutomaticEvaluationRequestDto request, HttpServletRequest httpRequest) {
    NetworkContext context = network.resolve(httpRequest);
    request.setClientIp(context.clientIp());
    request.setGeoCountry(context.geoCountry());
    request.setGeoCity(context.geoCity());
    request.setAsn(context.asn());
    request.setVpnOrProxy(context.vpnOrProxy());
    return ResponseEntity.ok(service.evaluate(request));
  }
}


