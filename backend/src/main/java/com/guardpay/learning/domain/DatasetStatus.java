package com.guardpay.learning.domain;

/** Dataset lifecycle. INSUFFICIENT_DATA is a first-class outcome, not an error. */
public enum DatasetStatus {
  READY,
  INSUFFICIENT_DATA,
  INVALIDATED
}
