package com.guardpay.risk.signal;

/** Trusted origin of a collected signal — never the browser. */
public enum SignalSource {
  TRANSACTION_HISTORY,
  MERCHANT_HISTORY,
  PAYMENT_CONTEXT,
  URL_REPUTATION,
  DEVICE,
  NETWORK,
  AI,
  NOT_AVAILABLE
}
