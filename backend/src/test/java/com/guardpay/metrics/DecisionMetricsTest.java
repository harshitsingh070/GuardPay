package com.guardpay.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.RecommendedAction;
import org.junit.jupiter.api.Test;

class DecisionMetricsTest {

  @Test
  void recordsActionsLatencyAndAiAvailability() {
    DecisionMetrics metrics = new DecisionMetrics();
    metrics.record(10, "AVAILABLE", RecommendedAction.ALLOW);
    metrics.record(20, "UNAVAILABLE", RecommendedAction.VERIFY);
    metrics.record(30, "AVAILABLE", RecommendedAction.STOP);

    DecisionMetrics.Snapshot snapshot = metrics.snapshot();

    assertEquals(3, snapshot.decisions());
    assertEquals(1, snapshot.allow());
    assertEquals(1, snapshot.verify());
    assertEquals(1, snapshot.stop());
    assertEquals(20, snapshot.p50LatencyMs());
    assertEquals(30, snapshot.p95LatencyMs());
    assertEquals(3, snapshot.aiRequests());
    assertEquals(2, snapshot.aiAvailable());
    assertEquals(1, snapshot.aiUnavailable());
    assertTrue(snapshot.aiAvailabilityPct() > 66.6 && snapshot.aiAvailabilityPct() < 66.7);
  }

  @Test
  void emptySnapshotIsSafe() {
    DecisionMetrics.Snapshot snapshot = new DecisionMetrics().snapshot();

    assertEquals(0, snapshot.decisions());
    assertEquals(0, snapshot.p95LatencyMs());
    assertEquals(100.0, snapshot.aiAvailabilityPct());
  }
}
