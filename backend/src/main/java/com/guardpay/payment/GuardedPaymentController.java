package com.guardpay.payment;

import com.guardpay.common.net.NetworkContext;
import com.guardpay.common.net.NetworkContextResolver;
import com.guardpay.common.net.TrustedProxyNetworkContextResolver;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.VerificationConfirmRequestDto;
import com.guardpay.payment.dto.VerificationConfirmResponseDto;
import com.guardpay.user.DemoUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guarded payments for the authenticated account.
 *
 * <p>Accepts payment/business context only. Risk is derived server-side;
 * nothing here can force a decision. Thin by design.
 */
@RestController
@RequestMapping("/api/payments")
public class GuardedPaymentController {

  private final PaymentSafetyOrchestrator orchestrator;
  private final PaymentVerificationService verificationService;
  private final DemoUserService demoUser;
  private final NetworkContextResolver network;

  public GuardedPaymentController(
      PaymentSafetyOrchestrator orchestrator, PaymentVerificationService verificationService) {
    this(
        orchestrator,
        verificationService,
        null,
        new TrustedProxyNetworkContextResolver("127.0.0.1/32,::1/128", false));
  }

  @Autowired
  public GuardedPaymentController(
      PaymentSafetyOrchestrator orchestrator,
      PaymentVerificationService verificationService,
      DemoUserService demoUser,
      NetworkContextResolver network) {
    this.orchestrator = orchestrator;
    this.verificationService = verificationService;
    this.demoUser = demoUser;
    this.network = network;
  }

  @PostMapping("/guarded")
  public ResponseEntity<GuardedPaymentResponseDto> initiate(
      @Valid @RequestBody GuardedPaymentRequestDto request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      HttpServletRequest httpRequest) {
    NetworkContext context = network.resolve(httpRequest);
    request.setClientIp(context.clientIp());
    request.setGeoCountry(context.geoCountry());
    request.setGeoCity(context.geoCity());
    request.setAsn(context.asn());
    request.setVpnOrProxy(context.vpnOrProxy());
    return ResponseEntity.ok(orchestrator.initiate(request, idempotencyKey));
  }

  @PostMapping("/{paymentId}/verification/confirm")
  public ResponseEntity<VerificationConfirmResponseDto> confirmVerification(
      @PathVariable UUID paymentId,
      @Valid @RequestBody VerificationConfirmRequestDto request) {
    if (demoUser == null) {
      return ResponseEntity.ok(verificationService.confirm(paymentId, request.code()));
    }
    return ResponseEntity.ok(
        verificationService.confirmForUser(
            paymentId, request.code(), demoUser.currentUser().getId()));
  }
}

