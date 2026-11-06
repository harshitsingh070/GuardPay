package com.guardpay.auth;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Periodic removal of idle/absolutely expired sessions. */
@Component
public class AuthSessionCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(AuthSessionCleanupJob.class);

  private final AuthSessionRepository sessions;

  public AuthSessionCleanupJob(AuthSessionRepository sessions) {
    this.sessions = sessions;
  }

  @Scheduled(
      initialDelayString = "${guardpay.auth.session-cleanup-initial-delay-ms:60000}",
      fixedDelayString = "${guardpay.auth.session-cleanup-interval-ms:3600000}")
  @Transactional
  public void purgeExpired() {
    int deleted = sessions.deleteExpired(OffsetDateTime.now(ZoneOffset.UTC));
    if (deleted > 0) {
      log.info("Removed {} expired authentication session(s)", deleted);
    }
  }
}
