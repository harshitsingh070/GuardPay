package com.guardpay.paypal.dto;

/** PayPal order created by the backend: identifiers for the approval flow only. */
public record CreatedPaypalOrder(String orderId, String status, String approvalUrl) {}
