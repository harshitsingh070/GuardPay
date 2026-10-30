package com.guardpay.evaluation;

public record RecipientComparison(
    String configuration,
    BenchmarkMode mode,
    boolean recipientIntelligence,
    BenchmarkMetrics metrics,
    RecipientMetrics recipientMetrics) {}
