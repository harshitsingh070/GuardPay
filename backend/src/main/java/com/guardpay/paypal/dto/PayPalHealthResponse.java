package com.guardpay.paypal.dto;

/**
 * Safe development health payload. Contains status labels only — never
 * credentials, tokens, or PayPal internals.
 */
public record PayPalHealthResponse(String status, String provider, String environment) {}
