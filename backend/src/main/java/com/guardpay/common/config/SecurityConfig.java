package com.guardpay.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security plumbing for the backend foundation.
 *
 * <p>Public (development stage, no authentication yet):
 * <ul>
 *   <li>{@code GET /api/health} — liveness probe.</li>
 *   <li>{@code POST /api/risk/evaluate} — TEMPORARY for local testing; the
 *       authentication milestone (planned JWT) will protect it.</li>
 *   <li>{@code POST /api/risk/evaluate-automatic} — same temporary status;
 *       accepts context only, never client risk verdicts.</li>
 *   <li>{@code POST /api/device-signals/call-state} — called by a paired
 *       device holding the development signal token (the token is the check;
 *       user authentication arrives later).</li>
 *   <li>{@code GET /api/paypal/health} — TEMPORARY development probe for the
 *       PayPal Sandbox integration; will be protected with the rest.</li>
 *   <li>{@code POST /api/paypal/orders*} — TEMPORARY development flow for
 *       Sandbox orders + gated capture; will be protected with the rest.</li>
 *   <li>{@code POST /api/payments/guarded} — TEMPORARY guarded-payment flow;
 *       accepts business context only, never risk verdicts.</li>
 *   <li>{@code POST /api/payments/&#123;paymentId&#125;/verification/confirm} — the
 *       controlled VERIFY answer; the server decides the outcome.</li>
 *   <li>{@code GET /api/transactions*} — TEMPORARY read-only history; the
 *       optional user id scopes the query and cannot widen access.</li>
 *   <li>{@code POST /api/paypal/webhooks} — called by PayPal itself, so no
 *       user authentication by design; signature verification is the trust
 *       mechanism.</li>
 * </ul>
 * Everything else is denied by default. No users, tokens, or login flows exist yet.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/risk/evaluate")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/risk/evaluate-automatic")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/device-signals/call-state")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/paypal/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/paypal/orders", "/api/paypal/orders/*/capture")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments/guarded")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments/*/verification/confirm")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/transactions", "/api/transactions/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/paypal/webhooks")
                    .permitAll()
                    // CORS preflights must never require authentication,
                    // otherwise browsers block cross-origin API calls.
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
