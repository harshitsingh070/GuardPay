package com.guardpay.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedProxyNetworkContextResolverTest {

  @Test
  void untrustedPeerCannotSpoofForwardedAddressOrGeo() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("10.0.0.0/8", true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.9");
    request.addHeader("X-Forwarded-For", "198.51.100.7");
    request.addHeader("X-Geo-Country", "FR");
    request.addHeader("X-Vpn-Proxy", "true");

    NetworkContext context = resolver.resolve(request);

    assertEquals("203.0.113.9", context.clientIp());
    assertNull(context.geoCountry());
    assertNull(context.vpnOrProxy());
  }

  @Test
  void trustedProxySelectsFirstUntrustedHopFromTheRight() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("10.0.0.0/8", false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.4");

    assertEquals("198.51.100.7", resolver.resolve(request).clientIp());
  }

  @Test
  void fullyTrustedChainResolvesToLeftmostAddress() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("10.0.0.0/8", false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.4");

    assertEquals("10.0.0.1", resolver.resolve(request).clientIp());
  }

  @Test
  void malformedForwardedChainFallsBackToPeerAddress() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("10.0.0.0/8", false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Forwarded-For", "198.51.100.7, not-an-ip");

    assertEquals("10.0.0.5", resolver.resolve(request).clientIp());
  }

  @Test
  void trustedGeoHeadersAreValidatedAndParsed() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("10.0.0.0/8", true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Geo-Country", "fr");
    request.addHeader("X-Geo-City", "  Paris  ");
    request.addHeader("X-Asn", "64500");
    request.addHeader("X-Vpn-Proxy", "1");

    NetworkContext context = resolver.resolve(request);

    assertEquals("10.0.0.5", context.clientIp());
    assertEquals("FR", context.geoCountry());
    assertEquals("Paris", context.geoCity());
    assertEquals(64500, context.asn());
    assertEquals(Boolean.TRUE, context.vpnOrProxy());
  }

  @Test
  void invalidGeoValuesAreUnavailableRatherThanGuessed() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("10.0.0.0/8", true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Geo-Country", "FRA");
    request.addHeader("X-Asn", "-1");
    request.addHeader("X-Vpn-Proxy", "maybe");

    NetworkContext context = resolver.resolve(request);

    assertNull(context.geoCountry());
    assertNull(context.asn());
    assertNull(context.vpnOrProxy());
  }

  @Test
  void ipv4MappedForwardedAddressesAreNormalized() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("127.0.0.1/32", false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "::ffff:198.51.100.7");

    assertEquals("198.51.100.7", resolver.resolve(request).clientIp());
  }

  @Test
  void privateButUntrustedPeerIsRejected() {
    TrustedProxyNetworkContextResolver resolver =
        new TrustedProxyNetworkContextResolver("10.0.0.0/8", false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.168.1.5");
    request.addHeader("X-Forwarded-For", "198.51.100.7");

    NetworkContext context = resolver.resolve(request);

    assertEquals("192.168.1.5", context.clientIp());
    assertFalse("198.51.100.7".equals(context.clientIp()));
  }

  @Test
  void invalidTrustedProxyConfigurationFailsFast() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TrustedProxyNetworkContextResolver("not-a-cidr", false));
  }
}
