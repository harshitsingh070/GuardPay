package com.guardpay.evaluation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dev-versus-held-out comparison built from two already-computed reports. */
public record BenchmarkComparison(
    String generatedAt,
    String devDataset,
    String devDatasetSha256,
    String heldOutDataset,
    String heldOutDatasetSha256,
    Map<String, ModeComparison> modes) {

  public record ModeComparison(
      BenchmarkMode mode,
      BenchmarkMetrics dev,
      BenchmarkMetrics heldOut,
      Map<String, Double> deltas) {

    public ModeComparison {
      deltas =
          deltas == null
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(deltas));
    }
  }

  public BenchmarkComparison {
    modes =
        modes == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(modes));
  }

  public static BenchmarkComparison from(BenchmarkReport dev, BenchmarkReport heldOut) {
    if (dev.datasetRole() != BenchmarkDatasetRole.DEV) {
      throw new IllegalArgumentException("dev report must have dataset role DEV");
    }
    if (heldOut.datasetRole() != BenchmarkDatasetRole.HELD_OUT) {
      throw new IllegalArgumentException("held-out report must have dataset role HELD_OUT");
    }

    Map<String, ModeComparison> modes = new LinkedHashMap<>();
    for (BenchmarkMode mode : BenchmarkMode.values()) {
      BenchmarkMetrics devMetrics = dev.metrics().get(mode.name());
      BenchmarkMetrics heldOutMetrics = heldOut.metrics().get(mode.name());
      if (devMetrics == null || heldOutMetrics == null) {
        continue;
      }

      Map<String, Double> deltas = new LinkedHashMap<>();
      deltas.put(
          "truePositives",
          difference(heldOutMetrics.truePositives(), devMetrics.truePositives()));
      deltas.put(
          "falseNegatives",
          difference(heldOutMetrics.falseNegatives(), devMetrics.falseNegatives()));
      deltas.put(
          "falsePositives",
          difference(heldOutMetrics.falsePositives(), devMetrics.falsePositives()));
      deltas.put(
          "trueNegatives",
          difference(heldOutMetrics.trueNegatives(), devMetrics.trueNegatives()));
      deltas.put("precision", difference(heldOutMetrics.precision(), devMetrics.precision()));
      deltas.put("recall", difference(heldOutMetrics.recall(), devMetrics.recall()));
      deltas.put("f1", difference(heldOutMetrics.f1(), devMetrics.f1()));
      deltas.put(
          "falsePositiveRate",
          difference(heldOutMetrics.falsePositiveRate(), devMetrics.falsePositiveRate()));
      deltas.put(
          "classificationAccuracy",
          difference(
              heldOutMetrics.classificationAccuracy(), devMetrics.classificationAccuracy()));
      deltas.put(
          "legitimatePaymentFriction",
          difference(
              heldOutMetrics.legitimateVerified() + heldOutMetrics.legitimateStopped(),
              devMetrics.legitimateVerified() + devMetrics.legitimateStopped()));

      modes.put(
          mode.name(),
          new ModeComparison(mode, devMetrics, heldOutMetrics, deltas));
    }

    return new BenchmarkComparison(
        OffsetDateTime.now(ZoneOffset.UTC).toString(),
        dev.dataset(),
        dev.datasetSha256(),
        heldOut.dataset(),
        heldOut.datasetSha256(),
        modes);
  }

  private static Double difference(int heldOut, int dev) {
    return (double) heldOut - dev;
  }

  private static Double difference(Double heldOut, Double dev) {
    if (heldOut == null || dev == null) {
      return null;
    }
    return heldOut - dev;
  }
}
