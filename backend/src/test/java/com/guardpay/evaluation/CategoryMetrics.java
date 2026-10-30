package com.guardpay.evaluation;

import com.guardpay.risk.RecommendedAction;
import java.util.List;

/** Metrics for one primary category or one cross-cutting signal dimension. */
public record CategoryMetrics(
    String category,
    int totalScenarios,
    int evaluatedScenarios,
    int failedScenarios,
    int fraudScenarios,
    int legitimateScenarios,
    int truePositives,
    int falseNegatives,
    int falsePositives,
    int trueNegatives,
    Double precision,
    Double recall,
    Double f1,
    Double accuracy,
    int allowCount,
    int verifyCount,
    int stopCount,
    int fraudAllowed,
    int fraudVerified,
    int fraudStopped,
    int legitimateAllowed,
    int legitimateVerified,
    int legitimateStopped,
    int legitimatePaymentFriction) {

  public static CategoryMetrics from(String category, List<BenchmarkResult> allResults) {
    List<BenchmarkResult> results = allResults.stream().filter(BenchmarkResult::evaluated).toList();

    int fraud = 0;
    int legitimate = 0;
    int tp = 0;
    int fn = 0;
    int fp = 0;
    int tn = 0;
    int allow = 0;
    int verify = 0;
    int stop = 0;
    int fraudAllowed = 0;
    int fraudVerified = 0;
    int fraudStopped = 0;
    int legitimateAllowed = 0;
    int legitimateVerified = 0;
    int legitimateStopped = 0;

    for (BenchmarkResult result : results) {
      RecommendedAction action = result.actualAction();
      if (action == RecommendedAction.ALLOW) {
        allow++;
      } else if (action == RecommendedAction.VERIFY) {
        verify++;
      } else if (action == RecommendedAction.STOP) {
        stop++;
      }

      if (result.label() == BenchmarkLabel.FRAUD) {
        fraud++;
        if (action == RecommendedAction.ALLOW) {
          fn++;
          fraudAllowed++;
        } else {
          tp++;
          if (action == RecommendedAction.VERIFY) {
            fraudVerified++;
          } else if (action == RecommendedAction.STOP) {
            fraudStopped++;
          }
        }
      } else {
        legitimate++;
        if (action == RecommendedAction.ALLOW) {
          tn++;
          legitimateAllowed++;
        } else {
          fp++;
          if (action == RecommendedAction.VERIFY) {
            legitimateVerified++;
          } else if (action == RecommendedAction.STOP) {
            legitimateStopped++;
          }
        }
      }
    }

    int evaluated = results.size();
    Double precision = MetricMath.ratio(tp, tp + fp);
    Double recall = MetricMath.ratio(tp, tp + fn);

    return new CategoryMetrics(
        category,
        allResults.size(),
        evaluated,
        allResults.size() - evaluated,
        fraud,
        legitimate,
        tp,
        fn,
        fp,
        tn,
        precision,
        recall,
        MetricMath.f1(precision, recall),
        MetricMath.ratio(tp + tn, evaluated),
        allow,
        verify,
        stop,
        fraudAllowed,
        fraudVerified,
        fraudStopped,
        legitimateAllowed,
        legitimateVerified,
        legitimateStopped,
        legitimateVerified + legitimateStopped);
  }

  public Double falseNegativeRate() {
    return MetricMath.ratio(falseNegatives, fraudScenarios);
  }

  public Double falsePositiveRate() {
    return MetricMath.ratio(falsePositives, legitimateScenarios);
  }
}
