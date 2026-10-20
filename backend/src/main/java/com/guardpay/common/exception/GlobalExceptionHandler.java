package com.guardpay.common.exception;

import com.guardpay.common.dto.ApiError;
import com.guardpay.paypal.dto.OrderGateResponseDto;
import com.guardpay.paypal.exception.CaptureBlockedException;
import com.guardpay.paypal.exception.PayPalException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Global exception handling structure (basic).
 *
 * <p>Covers framework-level failures with a stable JSON shape. Business
 * exceptions (auth, payments, risk, PayPal, webhooks) arrive with their own
 * milestones and will extend this advice — not replace it.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + messageOf(fieldError))
            .collect(Collectors.joining("; "));
    log.warn("Validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), details);
    return error(HttpStatus.BAD_REQUEST, "Validation failed: " + details, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn(
        "Unreadable body for {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        ex.getMostSpecificCause().getMessage());
    return error(HttpStatus.BAD_REQUEST, "Malformed JSON request", request);
  }

  @ExceptionHandler(CaptureBlockedException.class)
  public ResponseEntity<OrderGateResponseDto> handleCaptureBlocked(CaptureBlockedException ex) {
    // 403 with the gate body — the stored server-side decision stands.
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            new OrderGateResponseDto(
                false, ex.getAction(), ex.getRiskLevel(), ex.getMessage(), ex.getOrderId()));
  }

  @ExceptionHandler(PayPalException.class)
  public ResponseEntity<ApiError> handlePayPal(PayPalException ex, HttpServletRequest request) {
    // Safe details never leave the server; log server-side with the PayPal status.
    log.error("PayPal call failed (status={}): {}", ex.getStatusCode(), ex.getMessage());
    int status = ex.getStatusCode();
    // A 4xx (other than auth) means PayPal rejected the request itself — e.g. an
    // order that is not approved yet — so surface the actionable reason. Real
    // PayPal outages (5xx, network) stay a generic 502.
    if (status >= 400 && status < 500 && status != 401 && status != 403) {
      return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }
    return error(HttpStatus.BAD_GATEWAY, "PayPal service unavailable", request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> handleResponseStatus(
      ResponseStatusException ex, HttpServletRequest request) {
    HttpStatus status =
        ex.getStatusCode() instanceof HttpStatus httpStatus
            ? httpStatus
            : HttpStatus.INTERNAL_SERVER_ERROR;
    String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
    return error(status, message, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
    // Deliberately generic in the response: never leak stack traces or internals
    // to clients. The full cause is logged server-side for diagnosis.
    log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status, String message, HttpServletRequest request) {
    ApiError body =
        new ApiError(
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            Instant.now());
    return ResponseEntity.status(status).body(body);
  }

  private String messageOf(FieldError fieldError) {
    return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "invalid";
  }
}
