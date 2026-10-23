package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Local text/URL detection must work without AI, keys, or network access. */
class DeterministicContentAnalyzerTest {

  private final DeterministicContentAnalyzer analyzer = new DeterministicContentAnalyzer();

  @Test
  void canonicalGiftCardScamIsDetectedLocally() {
    DeterministicContentAnalyzer.ContentAnalysis result =
        analyzer.analyze(
            "URGENT: your account is locked. Pay immediately with gift cards and send the codes.",
            "http://paypal.com.secure-login.example/pay");

    assertTrue(result.urgent());
    assertTrue(result.paymentInstruction());
    assertTrue(result.suspiciousUrl());
    assertTrue(result.scamPattern());
  }

  @Test
  void unexpectedInvoiceCombinedWithPaymentInstructionIsDetected() {
    DeterministicContentAnalyzer.ContentAnalysis result =
        analyzer.analyze(
            "You did not request this invoice. Send payment by wire transfer today.", null);

    assertTrue(result.unexpectedInvoice());
    assertTrue(result.paymentInstruction());
    assertTrue(result.scamPattern());
  }

  @Test
  void normalInvoiceAndHttpsLinkAreNotFlagged() {
    DeterministicContentAnalyzer.ContentAnalysis result =
        analyzer.analyze(
            "Thanks for your business. Invoice #123 for $45 is attached.",
            "https://shop.example.com/invoice/123");

    assertFalse(result.urgent());
    assertFalse(result.paymentInstruction());
    assertFalse(result.unexpectedInvoice());
    assertFalse(result.suspiciousUrl());
    assertFalse(result.scamPattern());
  }

  @Test
  void lookalikeAndIpAddressLinksAreStructuralRedFlags() {
    assertTrue(analyzer.analyze("Pay here", "https://paypal.com.evil.example").suspiciousUrl());
    assertTrue(analyzer.analyze("Pay here", "http://203.0.113.9/pay").suspiciousUrl());
    assertTrue(analyzer.analyze("Pay here", "http://shop.example.com/pay").suspiciousUrl());
  }

  @Test
  void missingContentProducesNoFalsePositives() {
    DeterministicContentAnalyzer.ContentAnalysis result = analyzer.analyze(null, null);

    assertFalse(result.urgent());
    assertFalse(result.paymentInstruction());
    assertFalse(result.unexpectedInvoice());
    assertFalse(result.suspiciousUrl());
    assertFalse(result.scamPattern());
  }
}
