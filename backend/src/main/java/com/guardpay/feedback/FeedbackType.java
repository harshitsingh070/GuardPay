package com.guardpay.feedback;

/** Structured reason category supplied with a feedback label. */
public enum FeedbackType {
  UNAUTHORIZED_PAYMENT,
  SCAM_OR_SOCIAL_ENGINEERING,
  GOODS_NOT_RECEIVED,
  FAKE_INVOICE,
  WRONG_RECIPIENT,
  LEGITIMATE_PAYMENT,
  OTHER,
  FALSE_POSITIVE,
  FALSE_NEGATIVE,
  DISPUTED,
  REFUNDED,
  REVERSED,
  USER_REPORTED
}
