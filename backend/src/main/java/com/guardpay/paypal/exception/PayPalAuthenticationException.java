package com.guardpay.paypal.exception;

/** PayPal rejected the request with 401/403 — credentials invalid or unauthorized. */
public class PayPalAuthenticationException extends PayPalException {

  public PayPalAuthenticationException(String message) {
    this(401, message);
  }

  public PayPalAuthenticationException(int statusCode, String message) {
    super(statusCode, message);
  }
}
