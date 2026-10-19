package com.guardpay.paypal.service;

/**
 * PayPal connectivity outcome. Status labels only — no tokens, no credentials.
 */
public record PayPalConnectionStatus(String status, String environment) {}
