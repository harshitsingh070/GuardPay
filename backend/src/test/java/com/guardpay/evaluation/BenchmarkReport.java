package com.guardpay.evaluation;

import java.util.List;
import java.util.Map;

public record BenchmarkReport(
    String generatedAt,
    String dataset,
    String datasetSha256,
    int scenarioCount,
    Map<String, BenchmarkMetrics> metrics,
    Map<String, Map<String, CategoryMetrics>> categories,
    Map<String, Map<String, CategoryMetrics>> signalDimensions,
    Map<String, ThresholdAnalysis> thresholdAnalysis,
    Map<String, List<String>> recommendations,
    Map<String, List<BenchmarkResult>> results,
    List<String> failures) {}
