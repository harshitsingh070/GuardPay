package com.guardpay.behavior;

/**
 * Availability state of a behavioral dimension. A dimension is
 * AVAILABLE when sufficient history exists, PARTIAL when some data is
 * present but not enough for reliable detection, and UNAVAILABLE when
 * there is no usable history.
 */
public enum BehavioralDimensionAvailability {
  AVAILABLE,
  PARTIAL,
  UNAVAILABLE
}
