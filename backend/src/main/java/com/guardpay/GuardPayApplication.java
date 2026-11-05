package com.guardpay;

import com.guardpay.common.config.DotenvPropertyInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GuardPay backend entry point.
 *
 * <p>Init scaffold only — package structure under {@code com.guardpay} is
 * reserved for later milestones (auth, user, transaction, recipient,
 * merchant, payment, paypal, webhook, risk, common). No business logic yet.
 */
@SpringBootApplication
@EnableScheduling
public class GuardPayApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(GuardPayApplication.class);
    app.addInitializers(new DotenvPropertyInitializer());
    app.run(args);
  }
}
