package com.guardpay.intelligence.domain;

/**
 * Recipient identifier kinds that can be resolved to a global entity.
 *
 * <p>{@code PHONE} is supported by the normalizer but is not collected by any
 * current integration, so it is documented as unsupported rather than
 * fabricated.
 */
public enum IdentifierType {
  EMAIL,
  PAYPAL_ACCOUNT_ID,
  MERCHANT_ID,
  DOMAIN,
  PHONE
}
