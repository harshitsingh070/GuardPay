package com.guardpay.ai;

/** AI service unreachable, misconfigured, or returned garbage. Message is always safe. */
public class AiUnavailableException extends RuntimeException {

  public AiUnavailableException(String message) {
    super(message);
  }
}
