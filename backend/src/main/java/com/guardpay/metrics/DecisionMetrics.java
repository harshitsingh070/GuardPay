package com.guardpay.metrics;

import com.guardpay.risk.RecommendedAction;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.springframework.stereotype.Component;

/**
 * In-memory, bounded decision instrumentation for the live dashboard.
 *
 * <p>Not a metrics backend: it keeps the last 512 decision latencies and
 * simple counters so the demo can show p50/p95 and AI availability without
 * adding Micrometer/Prometheus to the dependency set.
 */
@Component
public class DecisionMetrics {

  private static final int MAX_SAMPLES = 512;

  private final Deque<Long> latenciesMillis = new ArrayDeque<>();
  private long decisions;
  private long allow;
  private long verify;
  private long stop;
  private long aiRequests;
  private long aiAvailable;
  private long aiUnavailable;

  public synchronized void record(
      long durationMillis, String aiStatus, RecommendedAction action) {
    decisions++;
    switch (action) {
      case ALLOW -> allow++;
      case VERIFY -> verify++;
      case STOP -> stop++;
    }
    latenciesMillis.addLast(Math.max(0L, durationMillis));
    while (latenciesMillis.size() > MAX_SAMPLES) {
      latenciesMillis.removeFirst();
    }
    if ("AVAILABLE".equals(aiStatus)) {
      aiRequests++;
      aiAvailable++;
    } else if ("UNAVAILABLE".equals(aiStatus)) {
      aiRequests++;
      aiUnavailable++;
    }
  }

  public synchronized Snapshot snapshot() {
    long[] samples = latenciesMillis.stream().mapToLong(Long::longValue).toArray();
    Arrays.sort(samples);
    return new Snapshot(
        decisions,
        allow,
        verify,
        stop,
        percentile(samples, 50),
        percentile(samples, 95),
        samples.length,
        aiRequests,
        aiAvailable,
        aiUnavailable,
        aiRequests == 0 ? 100.0 : (aiAvailable * 100.0) / aiRequests);
  }

  private static long percentile(long[] sorted, int percentile) {
    if (sorted.length == 0) {
      return 0;
    }
    int index = (int) Math.ceil((percentile / 100.0) * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
  }

  public record Snapshot(
      long decisions,
      long allow,
      long verify,
      long stop,
      long p50LatencyMs,
      long p95LatencyMs,
      long latencySamples,
      long aiRequests,
      long aiAvailable,
      long aiUnavailable,
      double aiAvailabilityPct) {}
}
