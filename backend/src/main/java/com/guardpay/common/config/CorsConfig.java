package com.guardpay.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS policy for browser clients.
 *
 * <p>Local development default allows the React + Vite dev server
 * ({@code http://localhost:3000}) and the Android WebView shell
 * ({@code https://appassets.androidplatform.net}) to call the API at
 * {@code http://localhost:8080}. Override with the
 * {@code CORS_ALLOWED_ORIGINS} environment variable (comma-separated).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  /** Origin of the bundled Android WebView (see android/ and the APK build script). */
  private static final String ANDROID_WEBVIEW_ORIGIN = "https://appassets.androidplatform.net";

  private final List<String> allowedOrigins;

  public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
    var origins = new ArrayList<>(allowedOrigins);
    if (!origins.contains(ANDROID_WEBVIEW_ORIGIN)) {
      origins.add(ANDROID_WEBVIEW_ORIGIN);
    }
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
}
