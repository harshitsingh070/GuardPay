package com.guardpay.risk.signal;

import com.guardpay.behavior.AdaptiveBehavioralAnalyzer;
import com.guardpay.behavior.dto.BehavioralAnalysisRequest;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RiskEvaluationRequest;
import com.guardpay.risk.RiskEvaluationResult;
import com.guardpay.risk.correlation.CorrelationResult;
import com.guardpay.risk.correlation.RiskCorrelationEngine;
import com.guardpay.risk.correlation.dto.CorrelationFindingDto;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.risk.signal.dto.PaymentSignalDto;
import com.guardpay.device.DeviceCallState;
import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.ai.AiFindingAdapter;
import com.guardpay.ai.AiUnavailableException;
import com.guardpay.ai.dto.AiAnalysisRequest;
import com.guardpay.ai.dto.AiAnalysisResponse;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.metrics.DecisionMetrics;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.user.DemoUserService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Automatic evaluation pipeline: trusted context â†’ collected signals â†’
 * existing engine â†’ response. Nothing is persisted on this path (consistent
 * with the manual evaluation endpoint); history is only read.
 */
@Service
public class AutomaticRiskEvaluationService {

  /**
   * Prototype freshness rule: a device observation older than this is stale
   * and must never count as an active call.
   */
  public static final Duration DEVICE_STATE_MAX_AGE = Duration.ofMinutes(5);

  private static final Logger log = LoggerFactory.getLogger(AutomaticRiskEvaluationService.class);

  private final PaymentSignalCollectorService collector;
  private final DeterministicRiskEngine engine;
  private final DemoUserService demoUser;
  private final DeviceCallStateRepository deviceStates;
  private final RecipientRepository recipients;
  private final MerchantRepository merchants;
  private final AiAnalysisClient aiClient;
  private final AdaptiveBehavioralAnalyzer behavioralAnalyzer;
  private final DecisionMetrics metrics;
  private final RiskCorrelationEngine correlationEngine;

  /** Legacy/test constructor: metrics, behavioral profiling, and correlation are not recorded. */
  public AutomaticRiskEvaluationService(
      PaymentSignalCollectorService collector,
      DeterministicRiskEngine engine,
      DemoUserService demoUser,
      DeviceCallStateRepository deviceStates,
      RecipientRepository recipients,
      MerchantRepository merchants,
      AiAnalysisClient aiClient) {
    this(
        collector,
        engine,
        demoUser,
        deviceStates,
        recipients,
        merchants,
        aiClient,
        null,
        null,
        null);
  }

  /** Legacy/test constructor: behavioral profiling and correlation are not recorded. */
  public AutomaticRiskEvaluationService(
      PaymentSignalCollectorService collector,
      DeterministicRiskEngine engine,
      DemoUserService demoUser,
      DeviceCallStateRepository deviceStates,
      RecipientRepository recipients,
      MerchantRepository merchants,
      AiAnalysisClient aiClient,
      DecisionMetrics metrics) {
    this(
        collector,
        engine,
        demoUser,
        deviceStates,
        recipients,
        merchants,
        aiClient,
        metrics,
        null,
        null);
  }

  /** Test constructor: correlation is not recorded. */
  public AutomaticRiskEvaluationService(
      PaymentSignalCollectorService collector,
      DeterministicRiskEngine engine,
      DemoUserService demoUser,
      DeviceCallStateRepository deviceStates,
      RecipientRepository recipients,
      MerchantRepository merchants,
      AiAnalysisClient aiClient,
      DecisionMetrics metrics,
      AdaptiveBehavioralAnalyzer behavioralAnalyzer) {
    this(
        collector,
        engine,
        demoUser,
        deviceStates,
        recipients,
        merchants,
        aiClient,
        metrics,
        behavioralAnalyzer,
        null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AutomaticRiskEvaluationService(
      PaymentSignalCollectorService collector,
      DeterministicRiskEngine engine,
      DemoUserService demoUser,
      DeviceCallStateRepository deviceStates,
      RecipientRepository recipients,
      MerchantRepository merchants,
      AiAnalysisClient aiClient,
      DecisionMetrics metrics,
      AdaptiveBehavioralAnalyzer behavioralAnalyzer,
      RiskCorrelationEngine correlationEngine) {
    this.collector = collector;
    this.engine = engine;
    this.demoUser = demoUser;
    this.deviceStates = deviceStates;
    this.recipients = recipients;
    this.merchants = merchants;
    this.aiClient = aiClient;
    this.metrics = metrics;
    this.behavioralAnalyzer = behavioralAnalyzer;
    this.correlationEngine = correlationEngine;
  }

  public AutomaticEvaluationResponseDto evaluate(AutomaticEvaluationRequestDto dto) {
    long startedAt = System.nanoTime();
    UUID currentUserId = demoUser.currentUser().getId();
    SignalCollectionRequest collectionRequest =
        new SignalCollectionRequest(
            dto.getAmount(),
            dto.getCurrency(),
            currentUserId,
            dto.getRecipientId(),
            dto.getMerchantId(),
            dto.getTransactionId(),
            dto.getContentText(),
            dto.getContentUrl(),
            dto.getDeviceId(),
            dto.getDeviceFingerprint(),
            dto.getClientIp(),
            dto.getGeoCountry(),
            dto.getGeoCity(),
            dto.getAsn(),
            dto.getVpnOrProxy());

    DeviceCallSignal deviceSignal = resolveDeviceSignal(currentUserId, dto.getDeviceId());
    AutomaticSignalCollectionResult deterministic =
        collector.collect(collectionRequest, deviceSignal);

    // AI evidence (optional): consulted only when the caller supplied content.
    // Any AI problem degrades to UNAVAILABLE â€” deterministic signals decide alone.
    List<PaymentSignal> aiSignals = List.of();
    String aiStatus;
    if (hasContent(dto)) {
      try {
        AiAnalysisResponse analysis =
            aiClient.analyze(buildAiRequest(dto, deterministic, deviceSignal, currentUserId));
        aiSignals = AiFindingAdapter.toPaymentSignals(analysis.findings());
        aiStatus = "AVAILABLE";
      } catch (AiUnavailableException unavailable) {
        log.warn("AI evidence unavailable, continuing deterministically");
        aiStatus = "UNAVAILABLE";
      }
    } else {
      aiStatus = "NOT_REQUESTED";
    }

    // Adaptive behavioral profiling: compares the incoming payment against the
    // user's learned baseline. Never overrides the engine; produces complementary
    // evidence signals only. Fails safely — deterministic signals continue alone.
    List<PaymentSignal> behavioralSignals = List.of();
    String behavioralProfileStatus = "UNAVAILABLE";
    if (behavioralAnalyzer != null) {
      try {
        BehavioralAnalysisRequest behavioralRequest =
            new BehavioralAnalysisRequest(
                currentUserId,
                dto.getAmount(),
                dto.getCurrency(),
                OffsetDateTime.now(),
                dto.getRecipientId(),
                null,
                dto.getMerchantId(),
                dto.getDeviceId(),
                dto.getDeviceFingerprint(),
                dto.getGeoCountry(),
                dto.getGeoCity(),
                dto.getAsn(),
                dto.getVpnOrProxy(),
                dto.getTransactionId(),
                OffsetDateTime.now());
        List<PaymentSignal> bs = behavioralAnalyzer.analyze(behavioralRequest);
        behavioralSignals = bs != null ? bs : List.of();
        behavioralProfileStatus =
            behavioralSignals.isEmpty() ? "INSUFFICIENT_DATA" : "AVAILABLE";
      } catch (Exception e) {
        log.warn("Behavioral profiling unavailable, continuing deterministically", e);
        behavioralProfileStatus = "UNAVAILABLE";
      }
    }

    List<PaymentSignal> combined = new ArrayList<>(deterministic.signals());
    combined.addAll(aiSignals);
    combined.addAll(behavioralSignals);
    AutomaticSignalCollectionResult collection =
        new AutomaticSignalCollectionResult(List.copyOf(combined), deterministic.historicalAverage());

    // Correlation is evidence-only and fail-safe: an unavailable or broken
    // correlation pass contributes zero points and never changes the decision
    // beyond the deterministic engine's own scoring.
    CorrelationResult correlation;
    if (correlationEngine == null) {
      correlation = CorrelationResult.empty(CorrelationResult.UNAVAILABLE);
    } else {
      try {
        correlation = correlationEngine.evaluate(collection.signals());
      } catch (RuntimeException failure) {
        log.warn("Signal correlation failed, continuing deterministically", failure);
        correlation = CorrelationResult.unavailable();
      }
    }

    RiskEvaluationRequest engineRequest =
        AutomaticSignalAdapter.toRiskEvaluationRequest(dto.getAmount(), collection);
    RiskEvaluationResult result = engine.evaluate(engineRequest, correlation);

    log.info(
        "Risk evaluated: score={} level={} action={} ai={} device={} correlation={} triggered={}",
        result.score(),
        result.riskLevel(),
        result.recommendedAction(),
        aiStatus,
        describeDevice(deviceSignal),
        correlation.status(),
        collection.signals().stream()
            .filter(PaymentSignal::detected)
            .map(signal -> signal.type().name())
            .toList());

    List<PaymentSignalDto> signals =
        collection.signals().stream()
            .map(
                signal ->
                    new PaymentSignalDto(
                        signal.type(),
                        signal.detected(),
                        signal.source(),
                        signal.reason(),
                        signal.evidence(),
                        signal.severity(),
                        signal.confidence()))
            .toList();

    // Build a lookup of behavioral signal metadata by signal type name, so
    // that RiskFindingDto instances can carry the explanatory evidence
    // (severity, confidence, evidence) produced by the adaptive behavioral
    // analyzer.
    Map<String, PaymentSignal> behavioralSignalMap =
        collection.signals().stream()
            .filter(s -> s.source() == SignalSource.BEHAVIORAL_PROFILE)
            .collect(
                Collectors.toMap(
                    s -> s.type().name(),
                    Function.identity(),
                    (existing, replacement) -> existing));

    List<RiskFindingDto> findings =
        result.findings().stream()
            .map(
                finding -> {
                  PaymentSignal signal = behavioralSignalMap.get(finding.code());
                  if (signal != null) {
                    return new RiskFindingDto(
                        finding.code(),
                        finding.title(),
                        finding.description(),
                        finding.points(),
                        finding.triggered(),
                        signal.severity(),
                        signal.confidence(),
                        signal.deviationRatio(),
                        signal.observedValue(),
                        signal.expectedValue(),
                        signal.profileWindow(),
                        signal.contributingFactors(),
                        signal.evidence());
                  }
                  return new RiskFindingDto(
                      finding.code(),
                      finding.title(),
                      finding.description(),
                      finding.points(),
                      finding.triggered());
                })
            .toList();

    if (metrics != null) {
      metrics.record(
          (System.nanoTime() - startedAt) / 1_000_000L,
          aiStatus,
          result.recommendedAction());
    }

    List<CorrelationFindingDto> correlationFindings =
        result.correlationFindings().stream().map(CorrelationFindingDto::from).toList();

    return new AutomaticEvaluationResponseDto(
        result.score(),
        result.riskLevel(),
        result.recommendedAction(),
        result.explanation(),
        signals,
        findings,
        aiStatus,
        behavioralProfileStatus,
        correlationFindings,
        correlation.status());
  }

  /**
   * Resolves a device id to a trusted call signal. Unknown devices, missing
   * observations, and observations older than {@link #DEVICE_STATE_MAX_AGE}
   * all resolve to unavailable â€” never guessed, never an old "active".
   */
  private DeviceCallSignal resolveDeviceSignal(UUID userId, UUID deviceId) {
    if (userId == null || deviceId == null) {
      return new DeviceCallSignal(null, "NOT_AVAILABLE");
    }
    Optional<DeviceCallState> state = deviceStates.findByUserIdAndDeviceId(userId, deviceId);
    if (state.isEmpty() || state.get().getObservedAt() == null) {
      return new DeviceCallSignal(null, "NOT_AVAILABLE");
    }
    if (state
        .get()
        .getObservedAt()
        .isBefore(OffsetDateTime.now(ZoneOffset.UTC).minus(DEVICE_STATE_MAX_AGE))) {
      return new DeviceCallSignal(null, "STALE");
    }
    return new DeviceCallSignal(state.get().isActiveCall(), null);
  }

  private static boolean hasContent(AutomaticEvaluationRequestDto dto) {
    return (dto.getContentText() != null && !dto.getContentText().isBlank())
        || (dto.getContentUrl() != null && !dto.getContentUrl().isBlank());
  }

  private static String describeDevice(DeviceCallSignal signal) {
    if (signal.activeCall() == null) {
      return "unavailable(" + signal.unavailabilityReason() + ")";
    }
    return signal.activeCall() ? "active-call" : "no-call";
  }

  private AiAnalysisRequest buildAiRequest(
      AutomaticEvaluationRequestDto dto,
      AutomaticSignalCollectionResult deterministic,
      DeviceCallSignal device,
      UUID currentUserId) {
    String recipientName =
        dto.getRecipientId() == null
            ? null
            : recipients
                .findByIdAndUserId(dto.getRecipientId(), currentUserId)
                .map(r -> r.getName())
                .orElse(null);
    String merchantName =
        dto.getMerchantId() == null
            ? null
            : merchants.findById(dto.getMerchantId()).map(m -> m.getName()).orElse(null);
    return new AiAnalysisRequest(
        new AiAnalysisRequest.AiTransactionContext(
            dto.getAmount(),
            dto.getCurrency() != null ? dto.getCurrency() : "USD",
            merchantName,
            recipientName,
            deterministic.detected(PaymentSignalType.NEW_RECIPIENT),
            deterministic.detected(PaymentSignalType.UNKNOWN_MERCHANT),
            deterministic.detected(PaymentSignalType.UNEXPECTED_INVOICE)),
        new AiAnalysisRequest.AiContentContext(dto.getContentText(), dto.getContentUrl()),
        new AiAnalysisRequest.AiDeviceContext(device.activeCall()));
  }
}
