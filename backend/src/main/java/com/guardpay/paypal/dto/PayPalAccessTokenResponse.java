package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PayPal {@code POST /v1/oauth2/token} response. Carries the bearer token —
 * handle with care: never log instances of this record.
 */
public record PayPalAccessTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn) {}
