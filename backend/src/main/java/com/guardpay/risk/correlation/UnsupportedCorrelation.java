package com.guardpay.risk.correlation;

/**
 * A correlation idea that cannot be evaluated with the current signal set.
 * Recorded explicitly so the gap is documented rather than silently ignored.
 */
public record UnsupportedCorrelation(String code, String reason) {}
