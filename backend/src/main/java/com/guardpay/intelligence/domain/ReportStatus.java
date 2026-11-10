package com.guardpay.intelligence.domain;

/** Review state of a submitted fraud report. */
public enum ReportStatus {
  SUBMITTED,
  UNDER_REVIEW,
  ACCEPTED,
  REJECTED
}
