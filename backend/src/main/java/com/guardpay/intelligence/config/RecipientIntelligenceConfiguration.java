package com.guardpay.intelligence.config;

import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.identity.RecipientIdentifierNormalizer;
import com.guardpay.intelligence.service.RecipientReputationCalculator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the identity primitives from environment-backed configuration. */
@Configuration
@EnableConfigurationProperties(RecipientIntelligenceProperties.class)
public class RecipientIntelligenceConfiguration {

  @Bean
  public RecipientIdentifierNormalizer recipientIdentifierNormalizer() {
    return new RecipientIdentifierNormalizer();
  }

  @Bean
  public RecipientIdentifierHasher recipientIdentifierHasher(
      RecipientIntelligenceProperties properties) {
    return new RecipientIdentifierHasher(
        properties.getHmacKeys(), properties.getActiveKeyVersion());
  }

  @Bean
  public RecipientReputationCalculator recipientReputationCalculator() {
    return new RecipientReputationCalculator();
  }
}
