package com.guardpay.intelligence.domain;

/**
 * Outcome of a recipient intelligence lookup.
 *
 * <p>Missing data is never treated as clean data: {@code NO_DATA} means the
 * recipient has no global record, while {@code UNAVAILABLE} means the lookup
 * itself could not be performed.
 */
public enum MatchStatus {
  NO_DATA,
  NO_MATCH,
  MATCH_FOUND,
  UNAVAILABLE
}
