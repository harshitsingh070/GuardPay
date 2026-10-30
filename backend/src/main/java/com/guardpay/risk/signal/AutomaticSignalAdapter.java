package com.guardpay.risk.signal;

import com.guardpay.ai.AiFindingAdapter;
import com.guardpay.risk.RiskEvaluationRequest;
import java.math.BigDecimal;

/**
 * Maps collected evidence onto the existing engine contract.
 *
 * <p>Deterministic signals come from backend history/device data; AI-derived
 * codes (validated structured findings only) fold into the matching existing
 * engine flags — each flag fires at most once no matter how many AI codes
 * point at it, so repeated LLM evidence cannot inflate the score. Future-only
 * device/language/social inputs that are unavailable stay {@code false}.
 * No scoring logic lives here.
 */
public final class AutomaticSignalAdapter {

  private AutomaticSignalAdapter() {}

  public static RiskEvaluationRequest toRiskEvaluationRequest(
      BigDecimal amount, AutomaticSignalCollectionResult collection) {
    return new RiskEvaluationRequest(
        amount,
        collection.historicalAverage(),
        collection.detected(PaymentSignalType.ACTIVE_CALL),
        collection.detected(PaymentSignalType.NEW_RECIPIENT),
        collection.detected(PaymentSignalType.REPEATED_ATTEMPTS),
        collection.detected(PaymentSignalType.UNKNOWN_MERCHANT),
        collection.detected(PaymentSignalType.UNEXPECTED_INVOICE)
            || collection.detected(PaymentSignalType.UNEXPECTED_INVOICE_CONTEXT),
        collection.detected(PaymentSignalType.URGENT_LANGUAGE),
        collection.detected(PaymentSignalType.SUSPICIOUS_URL)
            || collection.detected(PaymentSignalType.SUSPICIOUS_URL_STRUCTURE),
        collection.detected(PaymentSignalType.SOCIAL_ENGINEERING_DETECTED)
            || AiFindingAdapter.SOCIAL_GROUP.stream().anyMatch(collection::detected),
        collection.detected(PaymentSignalType.MISSING_CONTEXT),
        collection.detected(PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY));
  }
}
