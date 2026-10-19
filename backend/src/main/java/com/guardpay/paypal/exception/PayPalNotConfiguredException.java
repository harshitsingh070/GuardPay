package com.guardpay.paypal.exception;

/**
 * PayPal credentials are absent, so no PayPal call can be attempted.
 * A configuration state (not a PayPal rejection): maps to the safe
 * "PayPal service unavailable" response via {@link PayPalException}.
 */
public class PayPalNotConfiguredException extends PayPalException {

  public PayPalNotConfiguredException(String message) {
    super(message);
  }
}
