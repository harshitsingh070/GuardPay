package com.guardpay.behavior;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract for the read-only behavioral endpoints. */
@WebMvcTest(BehavioralProfileController.class)
@Import(CorsConfig.class)
class BehavioralProfileControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private UserBehaviorProfileRepository profiles;
  @MockBean private BehavioralOutcomeEventRepository outcomeEvents;
  @MockBean private DemoUserService demoUser;

  private static final UUID USER_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    when(demoUser.currentUser()).thenReturn(user);
  }

  @Test
  void profileFallsBackToInsufficientDataWhenNoBaselineExists() throws Exception {
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/behavior/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.profileStatus").value("INSUFFICIENT_DATA"))
        .andExpect(jsonPath("$.amountBehavior.availability").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.behavioralDimensions.length()").value(6));
  }

  @Test
  void profileExposesEstablishedBaselineWithoutRawStatistics() throws Exception {
    UserBehaviorProfile profile = new UserBehaviorProfile();
    profile.setProfileStatus(UserBehaviorProfile.ProfileStatus.ESTABLISHED);
    profile.setTransactionCount(42);
    profile.setAmountMedian(new BigDecimal("45.00"));
    profile.setAmountMin(new BigDecimal("12.00"));
    profile.setAmountMax(new BigDecimal("310.00"));
    profile.setPeakHour(14);
    profile.setDeviceCount(3);
    profile.setGeoCountryCount(2);
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

    mockMvc
        .perform(get("/api/behavior/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileStatus").value("ESTABLISHED"))
        .andExpect(jsonPath("$.transactionCount").value(42))
        .andExpect(jsonPath("$.amountBehavior.availability").value("AVAILABLE"))
        .andExpect(jsonPath("$.timeBehavior.availability").value("AVAILABLE"))
        .andExpect(jsonPath("$.deviceBehavior.availability").value("AVAILABLE"))
        .andExpect(jsonPath("$.deviceBehavior.deviceCount").value(3))
        .andExpect(jsonPath("$.behavioralDimensions[0].dimension").value("amount"));
  }

  @Test
  void summaryReturnsInsufficientDataWithNoAnomalies() throws Exception {
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(outcomeEvents.findRecentNonLearnable(eq(USER_ID), any())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/behavior/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileStatus").value("INSUFFICIENT_DATA"))
        .andExpect(jsonPath("$.transactionCount").value(0))
        .andExpect(jsonPath("$.recentAnomalies.length()").value(0));

    verify(outcomeEvents).findRecentNonLearnable(eq(USER_ID), any());
  }

  @Test
  void summarySurfacesRecentExcludedOutcomes() throws Exception {
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(outcomeEvents.findRecentNonLearnable(eq(USER_ID), any()))
        .thenReturn(
            List.of(
                event(BehavioralOutcome.REFUNDED, OffsetDateTime.now(ZoneOffset.UTC)),
                event(BehavioralOutcome.CANCELLED, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)),
                event(BehavioralOutcome.COMPLETED, OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))));

    mockMvc
        .perform(get("/api/behavior/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentAnomalies.length()").value(3))
        .andExpect(jsonPath("$.recentAnomalies[0]", containsString("Refund excluded")))
        .andExpect(jsonPath("$.recentAnomalies[1]", containsString("cancelled or blocked")))
        .andExpect(jsonPath("$.recentAnomalies[2]", containsString("excluded as suspicious")));
  }

  @Test
  void summaryCapsAnomaliesForDisplay() throws Exception {
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(outcomeEvents.findRecentNonLearnable(eq(USER_ID), any()))
        .thenReturn(
            java.util.stream.IntStream.range(0, 9)
                .mapToObj(
                    index ->
                        event(
                            BehavioralOutcome.FAILED,
                            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(index)))
                .toList());

    mockMvc
        .perform(get("/api/behavior/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentAnomalies.length()").value(5));
  }

  private static BehavioralOutcomeEvent event(
      BehavioralOutcome outcome, OffsetDateTime occurredAt) {
    BehavioralOutcomeEvent event = new BehavioralOutcomeEvent();
    event.setOutcome(outcome);
    event.setLearnable(false);
    event.setSource("TEST");
    event.setOccurredAt(occurredAt);
    return event;
  }
}
