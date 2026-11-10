package com.guardpay.risk.correlation;

/**
 * Independent evidence dimensions used by the correlation engine.
 *
 * <p>Families describe <em>where</em> a signal came from, not how strong it
 * is. Two signals in the same family are treated as correlated observations
 * of one underlying fact (for example {@code NEW_DEVICE} and
 * {@code DEVICE_BEHAVIOR_DEVIATION}), so the engine never awards full
 * independence to both.
 */
public enum EvidenceFamily {
  AMOUNT,
  RECIPIENT,
  DEVICE,
  GEOLOCATION,
  NETWORK,
  VELOCITY,
  CONTENT,
  SOCIAL_ENGINEERING,
  TIME,
  CALL,
  BLOCKLIST,
  MERCHANT,
  BEHAVIORAL_AGGREGATE
}
