package com.guardpay.risk;

/**
 * Supplies the currently deployed calibration. Implementations must be
 * fail-safe: any storage/parse problem returns {@link RiskCalibration#defaults()}.
 */
public interface RiskCalibrationProvider {

  RiskCalibration activeCalibration();
}
