package com.guardpay.risk.signal;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Local, deterministic inspection of payment text and URLs.
 *
 * <p>This runs before (and independently of) the AI service. It only reports
 * concrete, explainable evidence; it never decides an action and never uses
 * client-supplied risk booleans. The AI can add findings, but cannot make the
 * detector forget what the local rules already saw.
 */
@Component
public class DeterministicContentAnalyzer {

  private static final int MAX_URL_LENGTH = 2048;

  private static final Pattern URGENCY =
      Pattern.compile(
          "\\b(urgent|immediately|act now|right away|final warning|last chance|"
              + "within\\s+\\d+\\s+(minutes?|hours?)|account\\s+(will be|is)\\s+"
              + "(closed|locked|suspended)|avoid\\s+(suspension|closure)|verify your account|"
              + "pay now|send\\s+(the\\s+)?payment\\s+now|asap)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern PAYMENT_INSTRUCTION =
      Pattern.compile(
          "\\b(gift\\s*cards?|itunes|steam\\s*card|google\\s*play\\s*card|bitcoin|btc|"
              + "crypto(currency)?|usdt|wire\\s*transfer|western\\s*union|moneygram|zelle|"
              + "cash\\s*app|venmo|friends\\s+and\\s+family|remote\\s+access|teamviewer|"
              + "anydesk|screen\\s*shar(e|ing)|one[- ]time\\s+(code|passcode)|otp|"
              + "verification\\s+code)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern INVOICE =
      Pattern.compile(
          "\\b(invoice|billing|payment request|final notice|overdue|unpaid|you owe|"
              + "remittance|refund)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern UNEXPECTED =
      Pattern.compile(
          "\\b(did not request|didn't request|unexpected|unsolicited|unauthorized|"
              + "you never ordered|not expecting)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern URL_IN_TEXT =
      Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

  private static final Pattern IPV4 =
      Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");

  private static final Set<String> SHORTENERS =
      Set.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "buff.ly", "ow.ly", "rb.gy");

  private static final Set<String> HIGH_RISK_TLDS =
      Set.of("tk", "ml", "ga", "cf", "gq", "zip", "mov", "click", "top", "work", "support");

  private static final List<String> OFFICIAL_BRANDS =
      List.of(
          "paypal.com",
          "paypal.me",
          "google.com",
          "apple.com",
          "microsoft.com",
          "amazon.com",
          "irs.gov");

  /** Evidence-only result. All booleans default to false when content is absent. */
  public record ContentAnalysis(
      boolean urgent,
      boolean paymentInstruction,
      boolean unexpectedInvoice,
      boolean suspiciousUrl,
      boolean scamPattern,
      String urgentEvidence,
      String paymentEvidence,
      String invoiceEvidence,
      String urlEvidence,
      String scamEvidence) {

    public static ContentAnalysis empty() {
      return new ContentAnalysis(
          false, false, false, false, false, null, null, null, null, null);
    }
  }

  public ContentAnalysis analyze(String text, String url) {
    String message = text == null ? "" : text.trim();
    String candidateUrl = firstNonBlank(url, firstUrl(message));

    boolean urgent = URGENCY.matcher(message).find();
    String urgencyMatch = firstMatch(URGENCY, message);

    boolean paymentInstruction = PAYMENT_INSTRUCTION.matcher(message).find();
    String paymentMatch = firstMatch(PAYMENT_INSTRUCTION, message);

    boolean invoice = INVOICE.matcher(message).find();
    boolean unexpected = UNEXPECTED.matcher(message).find();
    boolean unexpectedInvoice = invoice && (unexpected || urgent);

    UrlCheck urlCheck = checkUrl(candidateUrl);

    boolean scamPattern =
        (urgent && paymentInstruction)
            || (paymentInstruction && urlCheck.suspicious())
            || (unexpectedInvoice && paymentInstruction);

    List<String> scamParts = new java.util.ArrayList<>();
    if (urgent) {
      scamParts.add("urgency/pressure");
    }
    if (paymentInstruction) {
      scamParts.add("high-risk payment instruction");
    }
    if (urlCheck.suspicious()) {
      scamParts.add("suspicious link");
    }
    if (unexpectedInvoice) {
      scamParts.add("unexpected invoice");
    }
    String scamEvidence =
        scamPattern
            ? "Multiple independent scam indicators co-occur ("
                + String.join(", ", scamParts)
                + ")."
            : null;

    return new ContentAnalysis(
        urgent,
        paymentInstruction,
        unexpectedInvoice,
        urlCheck.suspicious(),
        scamPattern,
        urgent ? "Message contains urgency/pressure wording: \"" + urgencyMatch + "\"." : null,
        paymentInstruction
            ? "Message requests a high-risk payment method: \"" + paymentMatch + "\"."
            : null,
        unexpectedInvoice
            ? "Message combines invoice/payment-request wording with urgency or an unexpected request."
            : null,
        urlCheck.suspicious() ? urlCheck.evidence() : null,
        scamEvidence);
  }

  private record UrlCheck(boolean suspicious, String evidence) {
    static UrlCheck safe() {
      return new UrlCheck(false, null);
    }
  }

  private UrlCheck checkUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return UrlCheck.safe();
    }
    String value = raw.trim();
    if (value.length() > MAX_URL_LENGTH) {
      return new UrlCheck(true, "URL is unreasonably long.");
    }
    try {
      String withScheme =
          value.matches("(?i)^https?://.*") ? value : "https://" + value;
      URI uri = URI.create(withScheme);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      String host = uri.getHost();
      if (host == null || host.isBlank()) {
        return new UrlCheck(true, "URL has no parseable host.");
      }
      host = host.toLowerCase(Locale.ROOT);
      String[] labels = host.split("\\.");
      String reason = null;

      if (!"https".equals(scheme)) {
        reason = "link does not use HTTPS";
      } else if (uri.getUserInfo() != null) {
        reason = "link embeds credentials/user info";
      } else if (IPV4.matcher(host).matches()) {
        reason = "link uses a raw IP address";
      } else if (host.startsWith("xn--") || host.contains(".xn--")) {
        reason = "link uses a punycode/internationalized lookalike host";
      } else if (labels.length >= 5) {
        reason = "link uses excessive subdomains";
      } else if (SHORTENERS.contains(host)) {
        reason = "link is an anonymous URL shortener";
      } else if (HIGH_RISK_TLDS.contains(labels[labels.length - 1])) {
        reason = "link uses a high-abuse top-level domain";
      } else if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
        reason = "link uses a nonstandard port";
      } else {
        reason = brandLookalike(host);
      }

      if (reason == null) {
        return UrlCheck.safe();
      }
      return new UrlCheck(true, "Suspicious URL structure (" + reason + "; host: " + host + ").");
    } catch (IllegalArgumentException invalid) {
      return new UrlCheck(true, "URL is malformed.");
    }
  }

  private String brandLookalike(String host) {
    for (String official : OFFICIAL_BRANDS) {
      if (host.equals(official) || host.endsWith("." + official)) {
        return null;
      }
    }
    for (String official : OFFICIAL_BRANDS) {
      String brand = official.substring(0, official.indexOf('.'));
      if (host.contains(brand)) {
        return "host imitates \"" + brand + "\" but is not its official domain";
      }
    }
    return null;
  }

  private static String firstMatch(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1) : "";
  }

  private static String firstUrl(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    Matcher matcher = URL_IN_TEXT.matcher(text);
    return matcher.find() ? matcher.group() : null;
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second;
  }
}
