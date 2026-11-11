package com.guardpay.common.net;

/**
 * Server-observed network metadata for a request. Fields are null when they
 * could not be resolved from a trusted source; callers must never substitute
 * client-provided values.
 */
public record NetworkContext(
    String clientIp, String geoCountry, String geoCity, Integer asn, Boolean vpnOrProxy) {}
