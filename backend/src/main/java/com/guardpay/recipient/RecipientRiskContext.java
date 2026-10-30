package com.guardpay.recipient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RecipientRiskContext(
    boolean identified,
    String reason,
    int completedPaymentCount,
    boolean historyTruncated,
    int unsuccessfulAttemptCount,
    OffsetDateTime lastCompletedAt,
    BigDecimal medianCompletedAmount,
    boolean amountHistorySufficient,
    int priorAttemptsInWindow) {

  public static RecipientRiskContext unavailable(String reason) {
    return new RecipientRiskContext(
        false, reason, 0, false, 0, null, null, false, 0);
  }

  public boolean hasCompletedHistory() {
    return completedPaymentCount > 0;
  }
}
