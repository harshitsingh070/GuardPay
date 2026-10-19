package com.guardpay.common.exception;

import com.guardpay.common.dto.ApiError;
import com.guardpay.paypal.dto.OrderGateResponseDto;
import com.guardpay.paypal.exception.CaptureBlockedException;
import com.guardpay.paypal.exception.PayPalException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + messageOf(fieldError))
            .collect(Collectors.joining("; "));
    return error(HttpStatus.BAD_REQUEST, "Validation failed: " + details, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
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
    // 502 with a generic message — safe PayPal details never leave the server.
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
    // Deliberately generic: never leak stack traces or internals to clients.
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
