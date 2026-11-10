package com.guardpay.intelligence.identity;

import com.guardpay.intelligence.domain.IdentifierType;
import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes recipient identifiers before hashing.
 *
 * <p>Each identifier kind has an explicit versioned rule. Normalization is
 * deterministic and locale-independent so the same input always produces the
 * same digest. Phone support exists for future integrations but no current
 * flow collects phone numbers.
 */
public final class RecipientIdentifierNormalizer {

  public static final String EMAIL_VERSION = "email-v1";
  public static final String PAYPAL_ACCOUNT_VERSION = "paypal-account-v1";
  public static final String MERCHANT_ID_VERSION = "merchant-id-v1";
  public static final String DOMAIN_VERSION = "domain-v1";
  public static final String PHONE_VERSION = "phone-v1";

  private static final Pattern EMAIL =
      Pattern.compile(
          "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
              + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
  private static final Pattern PAYPAL_ACCOUNT = Pattern.compile("^[^\\s\\p{Cntrl}]{3,128}$");
  private static final Pattern MERCHANT_ID = Pattern.compile("^[^\\s\\p{Cntrl}]{1,128}$");
  private static final Pattern PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");
  private static final Pattern IPV4 =
      Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");

  private static final int MAX_EMAIL_LENGTH = 254;
  private static final int MAX_DOMAIN_LENGTH = 253;

  public CanonicalIdentifier normalize(IdentifierType type, String raw) {
    if (type == null) {
      throw new IllegalArgumentException("identifier type is required");
    }
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("identifier value is required");
    }
    String value = raw.trim();
    return switch (type) {
      case EMAIL -> new CanonicalIdentifier(type, normalizeEmail(value), EMAIL_VERSION);
      case PAYPAL_ACCOUNT_ID ->
          new CanonicalIdentifier(type, normalizePaypalAccount(value), PAYPAL_ACCOUNT_VERSION);
      case MERCHANT_ID ->
          new CanonicalIdentifier(type, normalizeMerchantId(value), MERCHANT_ID_VERSION);
      case DOMAIN -> new CanonicalIdentifier(type, normalizeDomain(value), DOMAIN_VERSION);
      case PHONE -> new CanonicalIdentifier(type, normalizePhone(value), PHONE_VERSION);
    };
  }

  public String normalizationVersion(IdentifierType type) {
    return switch (type) {
      case EMAIL -> EMAIL_VERSION;
      case PAYPAL_ACCOUNT_ID -> PAYPAL_ACCOUNT_VERSION;
      case MERCHANT_ID -> MERCHANT_ID_VERSION;
      case DOMAIN -> DOMAIN_VERSION;
      case PHONE -> PHONE_VERSION;
    };
  }

  /** Phone identifiers are supported but not collected by any current flow. */
  public boolean isCollected(IdentifierType type) {
    return type != null && type != IdentifierType.PHONE;
  }

  private static String normalizeEmail(String value) {
    if (value.length() > MAX_EMAIL_LENGTH) {
      throw new IllegalArgumentException("email is too long");
    }
    String lower = value.toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(lower).matches()) {
      throw new IllegalArgumentException("email is not valid");
    }
    return lower;
  }

  private static String normalizePaypalAccount(String value) {
    if (!PAYPAL_ACCOUNT.matcher(value).matches()) {
      throw new IllegalArgumentException("PayPal account identifier is not valid");
    }
    // PayPal account identifiers are case-sensitive; preserve the supplied case.
    return value;
  }

  private static String normalizeMerchantId(String value) {
    if (!MERCHANT_ID.matcher(value).matches()) {
      throw new IllegalArgumentException("merchant identifier is not valid");
    }
    return value;
  }

  private static String normalizeDomain(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    while (lower.endsWith(".")) {
      lower = lower.substring(0, lower.length() - 1);
    }
    if (lower.isEmpty() || lower.length() > MAX_DOMAIN_LENGTH || IPV4.matcher(lower).matches()) {
      throw new IllegalArgumentException("domain is not valid");
    }
    String ascii;
    try {
      ascii = IDN.toASCII(lower, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("domain is not valid", invalid);
    }
    if (!ascii.contains(".") || ascii.startsWith(".") || ascii.endsWith(".")) {
      throw new IllegalArgumentException("domain must contain a registrable label");
    }
    for (String label : ascii.split("\\.")) {
      if (label.isEmpty() || label.length() > 63) {
        throw new IllegalArgumentException("domain label is not valid");
      }
    }
    return ascii;
  }

  private static String normalizePhone(String value) {
    String compact = value.replaceAll("[\\s().-]", "");
    if (!PHONE.matcher(compact).matches()) {
      throw new IllegalArgumentException("phone number must be in +E.164 form");
    }
    return compact;
  }
}
