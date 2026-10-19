package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One HATEOAS link in PayPal order responses (approval URL lives here). */
public record PayPalLink(
    @JsonProperty("href") String href,
    @JsonProperty("rel") String rel,
    @JsonProperty("method") String method) {}
