package com.guardpay.evaluation;

import java.util.List;

final class MetricMath {

  private MetricMath() {}

  static Double ratio(int numerator, int denominator) {
    return denominator == 0 ? null : (double) numerator / denominator;
  }

  static Double f1(Double precision, Double recall) {
    if (precision == null || recall == null || precision + recall == 0.0) {
      return null;
    }
    return 2 * precision * recall / (precision + recall);
  }

  static double percentile(List<Double> values, double percentile) {
    if (values.isEmpty()) {
      return 0.0;
    }
    int index = (int) Math.ceil(percentile / 100.0 * values.size()) - 1;
    return values.get(Math.max(0, Math.min(index, values.size() - 1)));
  }
}
