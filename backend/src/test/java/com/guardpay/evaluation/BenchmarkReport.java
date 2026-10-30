package com.guardpay.evaluation;

import java.util.List;
import java.util.Map;

public record BenchmarkReport(
    String generatedAt,
    String dataset,
    int scenarioCount,
    Map<String, BenchmarkMetrics> metrics,
    Map<String, List<BenchmarkResult>> results,
    List<String> failures) {}
