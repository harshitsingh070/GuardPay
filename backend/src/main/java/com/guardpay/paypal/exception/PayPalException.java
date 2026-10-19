package com.guardpay.paypal.exception;

/**
 * Safe PayPal failure. Messages must contain HTTP statuses and generic
 * reasons only — never credentials, tokens, auth headers, or raw PayPal
 * response bodies.
 */
public class PayPalException extends RuntimeException {

  private final int statusCode;

  public PayPalException(String message) {
    this(-1, message);
  }

  public PayPalException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  /** HTTP status from PayPal, or -1 when no response was received. */
  public int getStatusCode() {
    return statusCode;
  }
}
