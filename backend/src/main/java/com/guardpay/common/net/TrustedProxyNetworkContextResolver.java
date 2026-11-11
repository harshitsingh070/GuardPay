package com.guardpay.common.net;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves client IP and optional GeoIP/VPN headers only when the immediate
 * peer is a configured trusted proxy. Forwarded addresses are read right to
 * left so the first untrusted hop is selected; the chain is rejected entirely
 * when any entry is malformed.
 */
@Component
public class TrustedProxyNetworkContextResolver implements NetworkContextResolver {

  private static final int MAX_FORWARDED_ENTRIES = 32;

  private final List<IpRange> trustedProxies;
  private final boolean geoHeadersEnabled;

  public TrustedProxyNetworkContextResolver(
      @Value("${guardpay.network.trusted-proxies:127.0.0.1/32,::1/128}") String trustedProxies,
      @Value("${guardpay.network.geo-headers-enabled:false}") boolean geoHeadersEnabled) {
    this.trustedProxies = parseTrustedProxies(trustedProxies);
    this.geoHeadersEnabled = geoHeadersEnabled;
  }

  @Override
  public NetworkContext resolve(HttpServletRequest request) {
    String remote = normalizeIp(request.getRemoteAddr());
    if (remote == null || !isTrusted(remote)) {
      return new NetworkContext(remote, null, null, null, null);
    }

    String clientIp = resolveForwardedClient(request.getHeader("X-Forwarded-For"), remote);
    if (!geoHeadersEnabled) {
      return new NetworkContext(clientIp, null, null, null, null);
    }
    return new NetworkContext(
        clientIp,
        country(request.getHeader("X-Geo-Country")),
        city(request.getHeader("X-Geo-City")),
        asn(request.getHeader("X-Asn")),
        vpn(request.getHeader("X-Vpn-Proxy")));
  }

  private String resolveForwardedClient(String header, String remote) {
    if (header == null || header.isBlank()) {
      return remote;
    }
    String[] entries = header.split(",", -1);
    if (entries.length > MAX_FORWARDED_ENTRIES) {
      return remote;
    }
    String leftmost = null;
    for (int i = entries.length - 1; i >= 0; i--) {
      String candidate = normalizeIp(entries[i]);
      if (candidate == null) {
        return remote;
      }
      leftmost = candidate;
      if (!isTrusted(candidate)) {
        return candidate;
      }
    }
    return leftmost == null ? remote : leftmost;
  }

  private boolean isTrusted(String ip) {
    InetAddress address = toAddress(ip);
    if (address == null) {
      return false;
    }
    for (IpRange range : trustedProxies) {
      if (range.matches(address)) {
        return true;
      }
    }
    return false;
  }

  private static List<IpRange> parseTrustedProxies(String configured) {
    List<IpRange> ranges = new ArrayList<>();
    if (configured != null) {
      for (String entry : configured.split(",")) {
        if (entry == null || entry.isBlank()) {
          continue;
        }
        ranges.add(IpRange.parse(entry.trim()));
      }
    }
    if (ranges.isEmpty()) {
      throw new IllegalArgumentException("guardpay.network.trusted-proxies must not be empty");
    }
    return List.copyOf(ranges);
  }

  private static String normalizeIp(String raw) {
    InetAddress address = toAddress(raw);
    return address == null ? null : address.getHostAddress();
  }

  private static InetAddress toAddress(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    if (value.isEmpty()) {
      return null;
    }
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1).trim();
    }
    if (value.startsWith("[")) {
      int end = value.indexOf(']');
      if (end < 0) {
        return null;
      }
      value = value.substring(1, end);
    } else {
      int colon = value.lastIndexOf(':');
      if (colon > 0 && value.indexOf(':') == colon) {
        String port = value.substring(colon + 1);
        if (!port.isEmpty() && port.chars().allMatch(Character::isDigit)) {
          value = value.substring(0, colon);
        }
      }
    }
    int zone = value.indexOf('%');
    if (zone >= 0) {
      value = value.substring(0, zone);
    }
    if (!value.contains(".") && !value.contains(":")) {
      return null;
    }
    if (!value.matches("[0-9a-fA-F:.]+")) {
      return null;
    }
    try {
      InetAddress address = InetAddress.getByName(value);
      byte[] bytes = address.getAddress();
      if (bytes.length == 16 && isIpv4Mapped(bytes)) {
        byte[] ipv4 = new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]};
        return InetAddress.getByAddress(ipv4);
      }
      return address;
    } catch (UnknownHostException invalid) {
      return null;
    }
  }

  private static boolean isIpv4Mapped(byte[] bytes) {
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        return false;
      }
    }
    return bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
  }

  private static String country(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().toUpperCase(Locale.ROOT);
    return value.matches("[A-Z]{2}") ? value : null;
  }

  private static String city(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    return value.isEmpty() || value.length() > 128 ? null : value;
  }

  private static Integer asn(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
      return null;
    }
    try {
      int parsed = Integer.parseInt(value);
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException overflow) {
      return null;
    }
  }

  private static Boolean vpn(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes" -> Boolean.TRUE;
      case "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  private record IpRange(InetAddress network, int prefixBits) {

    static IpRange parse(String value) {
      int slash = value.indexOf('/');
      String addressPart = slash < 0 ? value : value.substring(0, slash);
      InetAddress address = toAddress(addressPart);
      if (address == null) {
        throw new IllegalArgumentException("Invalid trusted proxy address: " + value);
      }
      int maxBits = address.getAddress().length * 8;
      int prefix = maxBits;
      if (slash >= 0) {
        try {
          prefix = Integer.parseInt(value.substring(slash + 1).trim());
        } catch (NumberFormatException invalid) {
          throw new IllegalArgumentException("Invalid trusted proxy prefix: " + value);
        }
      }
      if (prefix < 0 || prefix > maxBits) {
        throw new IllegalArgumentException("Trusted proxy prefix out of range: " + value);
      }
      return new IpRange(address, prefix);
    }

    boolean matches(InetAddress candidate) {
      byte[] expected = network.getAddress();
      byte[] actual = candidate.getAddress();
      if (expected.length != actual.length) {
        return false;
      }
      int fullBytes = prefixBits / 8;
      for (int i = 0; i < fullBytes; i++) {
        if (expected[i] != actual[i]) {
          return false;
        }
      }
      int remainingBits = prefixBits % 8;
      if (remainingBits == 0) {
        return true;
      }
      int mask = 0xFF << (8 - remainingBits);
      return (expected[fullBytes] & mask) == (actual[fullBytes] & mask);
    }
  }
}
