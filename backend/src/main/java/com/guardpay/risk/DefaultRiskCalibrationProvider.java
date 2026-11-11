package com.guardpay.risk;

import org.springframework.stereotype.Component;

/** Baseline provider used when no learning model is deployed. */
@Component
public class DefaultRiskCalibrationProvider implements RiskCalibrationProvider {

  @Override
  public RiskCalibration activeCalibration() {
    return RiskCalibration.defaults();
  }
}
