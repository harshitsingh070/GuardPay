package com.guardpay.risk.correlation;

/**
 * Availability of one normalized signal.
 *
 * <p>Only {@link #PRESENT} signals participate in a correlation rule.
 * {@link #ABSENT} means the backend looked and found nothing; {@link #UNKNOWN}
 * and {@link #UNAVAILABLE} mean the evidence could not be obtained at all and
 * must never be interpreted as suspicious.
 */
public enum SignalAvailability {
  PRESENT,
  ABSENT,
  UNKNOWN,
  UNAVAILABLE
}
