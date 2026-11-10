package com.guardpay.evaluation;

import java.util.List;
import java.util.Map;

public record BenchmarkReport(
    String generatedAt,
    String dataset,
    String datasetSha256,
    BenchmarkDatasetRole datasetRole,
    int scenarioCount,
    Map<String, BenchmarkMetrics> metrics,
    Map<String, BenchmarkMetrics> behavioralComparison,
    Map<String, BenchmarkMetrics> correlationComparison,
    Map<String, Map<String, CategoryMetrics>> categories,
    Map<String, Map<String, CategoryMetrics>> signalDimensions,
    Map<String, RecipientComparison> recipientComparison,
    Map<String, ThresholdAnalysis> thresholdAnalysis,
    Map<String, List<String>> recommendations,
    Map<String, List<BenchmarkResult>> results,
    List<String> failures) {}
