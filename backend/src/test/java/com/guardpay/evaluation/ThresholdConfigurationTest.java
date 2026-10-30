package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.RiskLevels;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThresholdConfigurationTest {

  @Test
  void productionBaselineMirrorsProductionBoundaries() {
    ThresholdConfiguration production = ThresholdConfiguration.production();

    assertTrue(production.baseline());
    assertEquals("production-baseline", production.name());
    assertEquals(RiskLevels.LOW_MAX, production.lowMax());
    assertEquals(RiskLevels.MEDIUM_MAX, production.mediumMax());
    assertEquals(RiskLevels.HIGH_MAX, production.highMax());

    assertEquals(RecommendedAction.ALLOW, production.actionFor(0));
    assertEquals(RecommendedAction.ALLOW, production.actionFor(39));
    assertEquals(RecommendedAction.VERIFY, production.actionFor(40));
    assertEquals(RecommendedAction.VERIFY, production.actionFor(84));
    assertEquals(RecommendedAction.STOP, production.actionFor(85));
    assertEquals(RecommendedAction.STOP, production.actionFor(100));
  }

  @Test
  void appliesBoundaryScoresInclusively() {
    ThresholdConfiguration configuration =
        new ThresholdConfiguration("test", 20, 60, 79, false);

    assertEquals(RiskLevel.LOW, configuration.levelFor(20));
    assertEquals(RiskLevel.MEDIUM, configuration.levelFor(21));
    assertEquals(RiskLevel.MEDIUM, configuration.levelFor(60));
    assertEquals(RiskLevel.HIGH, configuration.levelFor(61));
    assertEquals(RiskLevel.HIGH, configuration.levelFor(79));
    assertEquals(RiskLevel.CRITICAL, configuration.levelFor(80));

    assertEquals(RecommendedAction.ALLOW, configuration.actionFor(20));
    assertEquals(RecommendedAction.VERIFY, configuration.actionFor(21));
    assertEquals(RecommendedAction.VERIFY, configuration.actionFor(79));
    assertEquals(RecommendedAction.STOP, configuration.actionFor(80));
  }

  @Test
  void defaultsAreValidAndContainExactlyOneBaseline() {
    List<ThresholdConfiguration> defaults = ThresholdConfiguration.defaults();

    assertFalse(defaults.isEmpty());
    assertEquals(1, defaults.stream().filter(ThresholdConfiguration::baseline).count());
    for (ThresholdConfiguration configuration : defaults) {
      assertTrue(configuration.lowMax() < configuration.mediumMax());
      assertTrue(configuration.mediumMax() < configuration.highMax());
      assertTrue(configuration.highMax() <= 100);
    }
  }

  @Test
  void rejectsInvalidNamesBoundariesAndScores() {
    assertThrows(
        IllegalArgumentException.class, () -> new ThresholdConfiguration(" ", 39, 69, 84, false));
    assertThrows(
        IllegalArgumentException.class, () -> new ThresholdConfiguration("bad", -1, 69, 84, false));
    assertThrows(
        IllegalArgumentException.class, () -> new ThresholdConfiguration("bad", 39, 69, 101, false));
    assertThrows(
        IllegalArgumentException.class, () -> new ThresholdConfiguration("bad", 39, 39, 84, false));
    assertThrows(
        IllegalArgumentException.class, () -> new ThresholdConfiguration("bad", 70, 69, 84, false));

    ThresholdConfiguration production = ThresholdConfiguration.production();
    assertThrows(IllegalArgumentException.class, () -> production.actionFor(-1));
    assertThrows(IllegalArgumentException.class, () -> production.levelFor(101));
  }
}
