package com.guardpay.risk;

/** Final recommendation produced by the deterministic risk engine. Stored as {@code EnumType.STRING}. */
public enum RecommendedAction {
  ALLOW,
  VERIFY,
  STOP
}
