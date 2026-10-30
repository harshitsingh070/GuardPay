package com.guardpay.recipient;

import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class RecipientRiskEvaluator {

  public PaymentSignal newRecipient(RecipientRiskContext context) {
    if (!context.identified()) {
      return unavailable(
          PaymentSignalType.NEW_RECIPIENT,
          context.reason(),
          "No reliable recipient identity was provided.");
    }
    if (!context.hasCompletedHistory()) {
      return new PaymentSignal(
          PaymentSignalType.NEW_RECIPIENT,
          true,
          SignalSource.TRANSACTION_HISTORY,
          null,
          "No previous completed transaction exists for this recipient.");
    }
    String count =
        context.historyTruncated()
            ? "At least " + context.completedPaymentCount()
            : Integer.toString(context.completedPaymentCount());
    return new PaymentSignal(
        PaymentSignalType.NEW_RECIPIENT,
        false,
        SignalSource.TRANSACTION_HISTORY,
        null,
        count + " previous completed transaction(s) with this recipient.");
  }

  public PaymentSignal amountAnomaly(
      BigDecimal currentAmount,
      RecipientRiskContext context,
      boolean accountWideAmountFlagged) {
    if (currentAmount == null) {
      return unavailable(
          PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY,
          "AMOUNT_UNAVAILABLE",
          "No amount was supplied.");
    }
    if (!context.identified()) {
      return unavailable(
          PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY,
          context.reason(),
          "No reliable recipient identity was provided.");
    }
    if (!context.amountHistorySufficient() || context.medianCompletedAmount() == null) {
      return unavailable(
          PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY,
          "INSUFFICIENT_RECIPIENT_HISTORY",
          "At least "
              + RecipientHistoryService.MIN_AMOUNT_SAMPLES
              + " previous completed payments to this recipient are required to judge the amount.");
    }
    if (accountWideAmountFlagged) {
      return unavailable(
          PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY,
          "ACCOUNT_WIDE_AMOUNT_FLAGGED",
          "The account-wide large-amount signal already covers this payment.");
    }

    BigDecimal threshold =
        context
            .medianCompletedAmount()
            .multiply(BigDecimal.valueOf(DeterministicRiskEngine.LARGE_AMOUNT_MULTIPLIER));
    boolean hit = currentAmount.compareTo(threshold) >= 0;
    return new PaymentSignal(
        PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY,
        hit,
        SignalSource.TRANSACTION_HISTORY,
        null,
        hit
            ? currentAmount.toPlainString()
                + " is at least "
                + DeterministicRiskEngine.LARGE_AMOUNT_MULTIPLIER
                + "x this recipient's median completed payment of "
                + context.medianCompletedAmount().toPlainString()
                + " ("
                + context.completedPaymentCount()
                + " payments)."
            : currentAmount.toPlainString()
                + " is within this recipient's normal range (median "
                + context.medianCompletedAmount().toPlainString()
                + ").");
  }

  public PaymentSignal repeatedAttempts(int accountPriorAttempts, RecipientRiskContext context) {
    int accountTotal = accountPriorAttempts + 1;
    boolean hit = accountTotal >= DeterministicRiskEngine.REPEATED_ATTEMPT_THRESHOLD;
    int recipientTotal =
        context.identified() ? context.priorAttemptsInWindow() + 1 : 0;
    int windowMinutes = DeterministicRiskEngine.REPEATED_ATTEMPT_WINDOW_MINUTES;

    String evidence;
    if (hit && recipientTotal >= DeterministicRiskEngine.REPEATED_ATTEMPT_THRESHOLD) {
      evidence =
          recipientTotal
              + " of "
              + accountTotal
              + " payment attempts in the last "
              + windowMinutes
              + " minutes were to this recipient.";
    } else if (hit) {
      evidence =
          accountTotal
              + " payment attempts were made in the last "
              + windowMinutes
              + " minutes.";
    } else if (context.identified()) {
      evidence =
          "No unusual rate of payment attempts to this recipient in the last "
              + windowMinutes
              + " minutes.";
    } else {
      evidence =
          "No unusual rate of payment attempts in the last "
              + windowMinutes
              + " minutes.";
    }

    return new PaymentSignal(
        PaymentSignalType.REPEATED_ATTEMPTS,
        hit,
        SignalSource.TRANSACTION_HISTORY,
        null,
        evidence);
  }

  private static PaymentSignal unavailable(
      PaymentSignalType type, String reason, String evidence) {
    return new PaymentSignal(
        type, false, SignalSource.TRANSACTION_HISTORY, reason, evidence);
  }
}
