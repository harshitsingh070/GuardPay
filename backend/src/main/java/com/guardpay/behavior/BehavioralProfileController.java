package com.guardpay.behavior;

import com.guardpay.behavior.dto.BehavioralProfileDto;
import com.guardpay.behavior.dto.BehavioralProfileSummaryDto;
import com.guardpay.behavior.dto.HighValueBaselineDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only behavioral profile endpoints for the authenticated user.
 *
 * <p>Never exposes another user's profile. All ownership is enforced
 * server-side through the authenticated session.
 */
@RestController
@RequestMapping("/api/behavior")
public class BehavioralProfileController {

  private static final Duration ANOMALY_WINDOW = Duration.ofDays(30);
  private static final int MAX_ANOMALIES = 5;
  private static final DateTimeFormatter ANOMALY_DATE =
      DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ROOT);

  private final UserBehaviorProfileRepository profiles;
  private final BehavioralOutcomeEventRepository outcomeEvents;
  private final DemoUserService demoUser;
  private final BehavioralProfileService profileService;

  public BehavioralProfileController(
      UserBehaviorProfileRepository profiles,
      BehavioralOutcomeEventRepository outcomeEvents,
      DemoUserService demoUser) {
    this(profiles, outcomeEvents, demoUser, null);
  }

  @Autowired
  public BehavioralProfileController(
      UserBehaviorProfileRepository profiles,
      BehavioralOutcomeEventRepository outcomeEvents,
      DemoUserService demoUser,
      @Autowired(required = false) BehavioralProfileService profileService) {
    this.profiles = profiles;
    this.outcomeEvents = outcomeEvents;
    this.demoUser = demoUser;
    this.profileService = profileService;
  }

  /**
   * Currency-specific high-value spending baseline for the authenticated user.
   */
  @GetMapping("/high-value/baseline")
  public ResponseEntity<HighValueBaselineDto> highValueBaseline(
      @RequestParam(value = "currency", required = false, defaultValue = "USD") String currency) {
    User user = demoUser.currentUser();
    if (profileService == null) {
      return ResponseEntity.ok(
          new HighValueBaselineDto(
              currency != null ? currency : "USD",
              "UNAVAILABLE",
              0,
              null, null, null, null, null, null, null,
              false));
    }
    HighValueBaselineDto baseline = profileService.getHighValueBaseline(user.getId(), currency);
    return ResponseEntity.ok(baseline);
  }

  /**
   * Returns the authenticated user's behavioral profile: status, dimensions,
   * and normal behavior ranges. No raw statistics that could enable profile
   * poisoning.
   */
  @GetMapping("/profile")
  public ResponseEntity<BehavioralProfileDto> profile() {
    User user = demoUser.currentUser();
    UUID userId = user.getId();

    com.guardpay.behavior.UserBehaviorProfile profile = profiles.findByUserId(userId).orElse(null);

    if (profile == null) {
      return ResponseEntity.ok(
          new BehavioralProfileDto(
              userId,
              "INSUFFICIENT_DATA",
              0,
              null,
              null,
              new BehavioralProfileDto.AmountBehavior("UNAVAILABLE", null, null, null, null, null),
              new BehavioralProfileDto.FrequencyBehavior("UNAVAILABLE", null, null, null),
              new BehavioralProfileDto.TimeBehavior(
                  "UNAVAILABLE", null, null, null, List.of()),
              new BehavioralProfileDto.RecipientBehavior(
                  "UNAVAILABLE", 0, 0, null, null),
              new BehavioralProfileDto.LocationBehavior("UNAVAILABLE", 0, List.of()),
              new BehavioralProfileDto.DeviceBehavior("UNAVAILABLE", 0, List.of()),
              List.of(
                  new BehavioralProfileDto.BehavioralDimensionStatus(
                      "amount", "UNAVAILABLE", "No transaction history"),
                  new BehavioralProfileDto.BehavioralDimensionStatus(
                      "frequency", "UNAVAILABLE", "No transaction history"),
                  new BehavioralProfileDto.BehavioralDimensionStatus(
                      "time", "UNAVAILABLE", "No transaction history"),
                  new BehavioralProfileDto.BehavioralDimensionStatus(
                      "recipient", "UNAVAILABLE", "No transaction history"),
                  new BehavioralProfileDto.BehavioralDimensionStatus(
                      "location", "UNAVAILABLE", "No transaction history"),
                  new BehavioralProfileDto.BehavioralDimensionStatus(
                      "device", "UNAVAILABLE", "No transaction history"))));
    }

    String status = profile.getProfileStatus() != null ? profile.getProfileStatus().name() : "INSUFFICIENT_DATA";
    Integer txCount = profile.getTransactionCount();

    BehavioralProfileDto.AmountBehavior amount =
        profile.getAmountMedian() != null
            ? new BehavioralProfileDto.AmountBehavior(
                "AVAILABLE",
                profile.getAmountMedian(),
                profile.getAmountMin(),
                profile.getAmountMax(),
                profile.getAmountP90(),
                profile.getAmountP95())
            : new BehavioralProfileDto.AmountBehavior("UNAVAILABLE", null, null, null, null, null);

    BehavioralProfileDto.FrequencyBehavior frequency =
        profile.getFrequencyPerDay() != null
            ? new BehavioralProfileDto.FrequencyBehavior(
                "AVAILABLE", profile.getFrequencyPerDay(), profile.getFrequencyPerWeek(), profile.getAvgIntervalMinutes())
            : new BehavioralProfileDto.FrequencyBehavior("UNAVAILABLE", null, null, null);

    BehavioralProfileDto.TimeBehavior time =
        profile.getPeakHour() != null
            ? new BehavioralProfileDto.TimeBehavior(
                "AVAILABLE", profile.getPeakHour(),
                profile.getWeekendPaymentRatio(),
                profile.getNightPaymentRatio(), List.of(profile.getPeakHour()))
            : new BehavioralProfileDto.TimeBehavior("UNAVAILABLE", null, null, null, List.of());

    BehavioralProfileDto.RecipientBehavior recipient =
        txCount != null && txCount > 0
            ? new BehavioralProfileDto.RecipientBehavior(
                "AVAILABLE",
                profile.getUniqueRecipientCount(),
                profile.getEstablishedRecipientCount(),
                profile.getNewRecipientRatio(),
                null)
            : new BehavioralProfileDto.RecipientBehavior("UNAVAILABLE", 0, 0, null, null);

    BehavioralProfileDto.LocationBehavior location =
        profile.getGeoCountryCount() > 0
            ? new BehavioralProfileDto.LocationBehavior("AVAILABLE", profile.getGeoCountryCount(), List.of())
            : new BehavioralProfileDto.LocationBehavior("UNAVAILABLE", 0, List.of());

    BehavioralProfileDto.DeviceBehavior device =
        profile.getDeviceCount() > 0
            ? new BehavioralProfileDto.DeviceBehavior("AVAILABLE", profile.getDeviceCount(), List.of())
            : new BehavioralProfileDto.DeviceBehavior("UNAVAILABLE", 0, List.of());

    return ResponseEntity.ok(
        new BehavioralProfileDto(
            userId,
            status,
            txCount,
            profile.getLastUpdatedAt(),
            profile.getDataQualityScore() != null ? profile.getDataQualityScore().toString() : null,
            amount,
            frequency,
            time,
            recipient,
            location,
            device,
            List.of(
                new BehavioralProfileDto.BehavioralDimensionStatus(
                    "amount", amount.availability(), "Historical transaction amounts"),
                new BehavioralProfileDto.BehavioralDimensionStatus(
                    "frequency", frequency.availability(), "Payment cadence"),
                new BehavioralProfileDto.BehavioralDimensionStatus(
                    "time", time.availability(), "Hour-of-day distribution"),
                new BehavioralProfileDto.BehavioralDimensionStatus(
                    "recipient", recipient.availability(), "Counterparty relationships"),
                new BehavioralProfileDto.BehavioralDimensionStatus(
                    "location", location.availability(), "Geographic distribution"),
                new BehavioralProfileDto.BehavioralDimensionStatus(
                    "device", device.availability(), "Device identity"))));
  }

  /**
   * Returns a safe summary of established behavior and recent anomalies for
   * dashboard display. No raw statistics.
   */
  @GetMapping("/summary")
  public ResponseEntity<BehavioralProfileSummaryDto> summary() {
    User user = demoUser.currentUser();
    com.guardpay.behavior.UserBehaviorProfile profile = profiles.findByUserId(user.getId()).orElse(null);
    List<String> anomalies = recentAnomalies(user.getId());

    if (profile == null || profile.getProfileStatus() == com.guardpay.behavior.UserBehaviorProfile.ProfileStatus.INSUFFICIENT_DATA) {
      return ResponseEntity.ok(
          new BehavioralProfileSummaryDto(
              "INSUFFICIENT_DATA",
              profile == null ? 0 : profile.getTransactionCount(),
              profile == null ? null : profile.getLastUpdatedAt(),
              "No established baseline",
              "No established pattern",
              "No recipient history",
              "No device history",
              anomalies));
    }

    String status = profile.getProfileStatus().name();
    Integer txCount = profile.getTransactionCount();

    String amountRange;
    if (profile.getAmountMedian() != null && profile.getAmountP90() != null) {
      amountRange = "Typically " + profile.getAmountMedian() + " (up to " + profile.getAmountP90() + ")";
    } else {
      amountRange = "No established range";
    }

    String typicalHours;
    if (profile.getPeakHour() != null) {
      typicalHours = "Most active around " + String.format("%02d:00", profile.getPeakHour());
    } else {
      typicalHours = "No established pattern";
    }

    String recipientFamiliarity;
    if (profile.getUniqueRecipientCount() > 0) {
      recipientFamiliarity = profile.getEstablishedRecipientCount() + " of " + profile.getUniqueRecipientCount() + " recipients established";
    } else {
      recipientFamiliarity = "No recipient history";
    }

    String deviceFamiliarity;
    if (profile.getDeviceCount() > 0) {
      deviceFamiliarity = profile.getDeviceCount() + " known device(s)";
    } else {
      deviceFamiliarity = "No device history";
    }

    return ResponseEntity.ok(
        new BehavioralProfileSummaryDto(
            status,
            txCount,
            profile.getLastUpdatedAt(),
            amountRange,
            typicalHours,
            recipientFamiliarity,
            deviceFamiliarity,
            anomalies));
  }

  /** Excluded outcomes from the last 30 days, newest first, capped for display. */
  private List<String> recentAnomalies(UUID userId) {
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minus(ANOMALY_WINDOW);
    return outcomeEvents.findRecentNonLearnable(userId, since).stream()
        .limit(MAX_ANOMALIES)
        .map(
            event ->
                describe(event)
                    + " \u00b7 "
                    + event.getOccurredAt().atZoneSameInstant(ZoneOffset.UTC)
                        .format(ANOMALY_DATE))
        .toList();
  }

  private static String describe(BehavioralOutcomeEvent event) {
    return switch (event.getOutcome()) {
      case REFUNDED -> "Refund excluded a payment from your baseline";
      case REVERSED -> "Reversal excluded a payment from your baseline";
      case DISPUTED -> "Dispute excluded a payment from your baseline";
      case FAILED -> "A payment failed";
      case CANCELLED -> "A payment was cancelled or blocked";
      case COMPLETED -> "A completed payment was excluded as suspicious";
    };
  }
}
