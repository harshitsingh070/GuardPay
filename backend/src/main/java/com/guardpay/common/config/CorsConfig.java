package com.guardpay.common.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Strict credentialed CORS policy for browser clients.
 *
 * <p>Wildcards are rejected because credentialed requests must name an exact
 * origin. Local development allows the React + Vite dev server
 * ({@code http://localhost:3000}) and the Android WebView shell
 * ({@code https://appassets.androidplatform.net}); override with
 * {@code CORS_ALLOWED_ORIGINS} (comma-separated).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  /** Origin of the bundled Android WebView (see android/ and the APK build script). */
  private static final String ANDROID_WEBVIEW_ORIGIN = "https://appassets.androidplatform.net";

  private final List<String> allowedOrigins;

  public CorsConfig(
      @Value(
              "${app.cors.allowed-origins:http://localhost:3000,https://appassets.androidplatform.net}")
          List<String> allowedOrigins) {
    LinkedHashSet<String> origins = new LinkedHashSet<>();
    if (allowedOrigins != null) {
      for (String candidate : allowedOrigins) {
        origins.add(validateOrigin(candidate));
      }
    }
    origins.add(ANDROID_WEBVIEW_ORIGIN);
    this.allowedOrigins = List.copyOf(origins);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(allowedOrigins.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }

  private static String validateOrigin(String candidate) {
    String origin = candidate == null ? "" : candidate.trim();
    if (origin.isEmpty() || origin.contains("*")) {
      throw new IllegalStateException(
          "CORS origins must be exact; wildcards are not allowed with credentials");
    }
    URI uri;
    try {
      uri = URI.create(origin);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid CORS origin: " + origin, invalid);
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!("http".equals(scheme) || "https".equals(scheme))
        || uri.getHost() == null
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
      throw new IllegalStateException("Invalid CORS origin: " + origin);
    }
    return scheme + "://" + uri.getRawAuthority();
  }
}
