package com.guardpay.feedback;

/** Bounded, machine-readable reason; no free-text user description is stored. */
public enum FeedbackReasonCode {
  SOCIAL_ENGINEERING,
  ACCOUNT_TAKEOVER,
  FAKE_INVOICE,
  FAKE_DELIVERY,
  ROMANCE_SCAM,
  INVESTMENT_SCAM,
  GOODS_NOT_RECEIVED,
  WRONG_RECIPIENT,
  UNAUTHORIZED_PAYMENT,
  OTHER,
  NONE
}
