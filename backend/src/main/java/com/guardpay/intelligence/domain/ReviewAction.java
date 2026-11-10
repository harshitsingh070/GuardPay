package com.guardpay.intelligence.domain;

/** Admin review decisions for a global recipient entity. */
public enum ReviewAction {
  CORROBORATE,
  CONFIRM_FRAUD,
  REJECT,
  CLEAR,
  REOPEN
}
