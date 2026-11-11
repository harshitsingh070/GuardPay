package com.guardpay.feedback;

/** Decouples feedback writes from the learning dataset lifecycle. */
public interface FeedbackChangeListener {

  void onFeedbackChanged(FraudFeedback feedback, String reason);
}
