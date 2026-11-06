package com.guardpay.behavior;

import com.guardpay.behavior.dto.BehavioralProfileDto;
import com.guardpay.behavior.dto.BehavioralProfileSummaryDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

  private final UserBehaviorProfileRepository profiles;
  private final DemoUserService demoUser;

  public BehavioralProfileController(
      UserBehaviorProfileRepository profiles, DemoUserService demoUser) {
    this.profiles = profiles;
    this.demoUser = demoUser;
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

    if (profile == null || profile.getProfileStatus() == com.guardpay.behavior.UserBehaviorProfile.ProfileStatus.INSUFFICIENT_DATA) {
      return ResponseEntity.ok(
          new BehavioralProfileSummaryDto(
              "INSUFFICIENT_DATA",
              0,
              null,
              "No established baseline",
              "No established pattern",
              "No recipient history",
              "No device history",
              List.of()));
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
            List.of()));
  }
}
