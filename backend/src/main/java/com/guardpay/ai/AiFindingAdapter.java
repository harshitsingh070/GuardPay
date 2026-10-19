package com.guardpay.ai;

import com.guardpay.ai.dto.AiFindingDto;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts validated AI findings into {@link PaymentSignal}s.
 *
 * <p>Boundary rules: only controlled-vocabulary codes convert (unknown codes
 * are skipped with a warning, never converted); confidence below
 * {@link #MIN_CONFIDENCE} is skipped. These become evidence the engine maps
 * onto its existing signals — they can never set a risk action directly.
 */
public final class AiFindingAdapter {

  private static final Logger log = LoggerFactory.getLogger(AiFindingAdapter.class);

  /** Minimum observation confidence for an AI finding to count as evidence. */
  public static final double MIN_CONFIDENCE = 0.5;

  /** AI codes that feed the engine's social-engineering signal (counted once). */
  public static final Set<PaymentSignalType> SOCIAL_GROUP =
      EnumSet.of(
          PaymentSignalType.IMPERSONATION,
          PaymentSignalType.PRESSURE_TO_PAY,
          PaymentSignalType.SECRECY_REQUEST,
          PaymentSignalType.SUSPICIOUS_PAYMENT_INSTRUCTION,
          PaymentSignalType.SCAM_PATTERN);

  private AiFindingAdapter() {}

  public static List<PaymentSignal> toPaymentSignals(List<AiFindingDto> findings) {
    if (findings == null) {
      return List.of();
    }
    List<PaymentSignal> signals = new ArrayList<>();
    for (AiFindingDto finding : findings) {
      PaymentSignalType type = mapCode(finding == null ? null : finding.code());
      if (type == null) {
        log.warn("Ignoring AI finding with unsupported code");
        continue;
      }
      if (!valid(finding)) {
        log.warn("Ignoring AI finding with invalid shape (code={})", type);
        continue;
      }
      signals.add(
          new PaymentSignal(
              type, true, SignalSource.AI, null, finding.evidence(),
              finding.severity(), finding.confidence()));
    }
    return List.copyOf(signals);
  }

  private static PaymentSignalType mapCode(String code) {
    if (code == null) {
      return null;
    }
    try {
      PaymentSignalType type = PaymentSignalType.valueOf(code);
      return type == PaymentSignalType.ACTIVE_CALL ? null : type;
    } catch (IllegalArgumentException unknown) {
      return null;
    }
  }

  private static boolean valid(AiFindingDto finding) {
    return finding.confidence() != null
        && finding.confidence() >= MIN_CONFIDENCE
        && finding.confidence() <= 1.0
        && finding.title() != null
        && !finding.title().isBlank()
        && finding.description() != null
        && !finding.description().isBlank()
        && finding.evidence() != null
        && !finding.evidence().isBlank();
  }
}
