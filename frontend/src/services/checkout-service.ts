// Payment data source for the checkout. The real implementation is the
// Spring Boot guarded-payment API; the mock is development-only and must be
// enabled explicitly (VITE_CHECKOUT_MOCK=true) so it can never be mistaken
// for a real payment.

import {
  capturePayPalOrder,
  confirmPaymentVerification,
  createGuardedPayment,
} from '@/services/api-client';
import type { CheckoutService } from '@/types/checkout';
import type {
  GuardedPaymentRequest,
  GuardedPaymentResponse,
  VerificationConfirmResponse,
} from '@/types/payment';
import type { PayPalCaptureResponse } from '@/types/paypal';

export const isMockCheckout = import.meta.env.VITE_CHECKOUT_MOCK === 'true';

export const realCheckoutService: CheckoutService = {
  createPayment(request: GuardedPaymentRequest, idempotencyKey: string) {
    return createGuardedPayment(request, idempotencyKey);
  },
  capturePayment(orderId: string) {
    return capturePayPalOrder(orderId);
  },
  confirmVerification(paymentId: string, code: string) {
    return confirmPaymentVerification(paymentId, code);
  },
};

export function getCheckoutService(): CheckoutService {
  return isMockCheckout ? mockCheckoutService : realCheckoutService;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function mockOrderId(): string {
  const suffix =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID().slice(0, 8).toUpperCase()
      : Math.random().toString(36).slice(2, 10).toUpperCase();
  return `MOCK-ORDER-${suffix}`;
}

const SUSPICIOUS_TEXT =
  /urgent|immediately|suspend|account|do not contact|gift card|verify|support department/i;

const MOCK_VERIFICATION_CODE = '123456';
const MOCK_MAX_ATTEMPTS = 3;
const mockVerificationAttempts = new Map<string, number>();

function mockVerification(paymentId: string) {
  return {
    verificationId: `mock-verification-${paymentId}`,
    status: 'PENDING' as const,
    expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    attemptsRemaining: MOCK_MAX_ATTEMPTS,
    simulated: true,
    channelHint:
      'Mock channel (development only) — the backend outbox holds the code in real mode.',
  };
}

function mockSignals(riskyText: boolean, amount: number) {
  return [
    {
      type: 'NEW_RECIPIENT',
      detected: true,
      source: 'TRANSACTION_HISTORY',
      reason: null,
      evidence: 'No previous completed transaction exists for this recipient.',
      severity: null,
      confidence: null,
    },
    {
      type: 'URGENT_LANGUAGE',
      detected: riskyText,
      source: riskyText ? 'AI' : 'NOT_AVAILABLE',
      reason: riskyText ? null : 'NOT_AVAILABLE_YET',
      evidence: riskyText ? 'The message uses urgent, pressuring language.' : '',
      severity: riskyText ? 'HIGH' : null,
      confidence: riskyText ? 0.9 : null,
    },
    {
      type: 'UNUSUAL_AMOUNT',
      detected: amount >= 500,
      source: 'TRANSACTION_HISTORY',
      reason: amount >= 500 ? null : 'INSUFFICIENT_HISTORY',
      evidence: amount >= 500 ? 'The amount is well above the usual range.' : '',
      severity: null,
      confidence: null,
    },
  ];
}

function mockFindings(status: 'ALLOW' | 'VERIFY' | 'STOP') {
  const findings = [
    {
      code: 'NEW_RECIPIENT',
      title: 'New recipient',
      description: 'This payment is going to a new recipient.',
      points: 20,
      triggered: true,
    },
    {
      code: 'UNUSUAL_AMOUNT',
      title: 'Unusually large amount',
      description: 'Payment amount is significantly higher than the normal amount.',
      points: 20,
      triggered: status !== 'ALLOW',
    },
    {
      code: 'URGENT_LANGUAGE',
      title: 'Urgent language',
      description: 'Urgent or pressuring language was detected.',
      points: 10,
      triggered: status !== 'ALLOW',
    },
    {
      code: 'SOCIAL_ENGINEERING',
      title: 'Known scam pattern',
      description: 'Behaviour is consistent with a known social-engineering scam pattern.',
      points: 20,
      triggered: status === 'STOP',
    },
  ];
  return findings;
}

/**
 * Deterministic, clearly-labelled mock used only for frontend development and
 * component tests. It never touches PayPal or the backend.
 */
export const mockCheckoutService: CheckoutService = {
  async createPayment(request: GuardedPaymentRequest): Promise<GuardedPaymentResponse> {
    await delay(700);
    const amount = Number(request.amount);
    const riskyText =
      SUSPICIOUS_TEXT.test(request.contentText ?? '') ||
      (request.contentUrl ?? '').trim() !== '';
    const stop = amount >= 2000 || (riskyText && amount >= 500);
    const verify = !stop && (riskyText || amount >= 500);
    const action = stop ? 'STOP' : verify ? 'VERIFY' : 'ALLOW';
    const riskLevel = stop ? 'CRITICAL' : verify ? 'MEDIUM' : 'LOW';
    const score = stop ? 100 : verify ? 45 : 10;
    const paymentId = `mock-${Date.now()}`;

    return {
      paymentId,
      risk: {
        score,
        riskLevel,
        recommendedAction: action,
        explanation: stop
          ? 'GuardPay flagged several high-risk signals and stopped this payment before a PayPal order was created.'
          : verify
            ? 'Some transaction signals need additional confirmation before this payment can continue.'
            : 'No significant risk signals were found for this payment.',
        automaticSignals: mockSignals(riskyText, amount),
        findings: mockFindings(action),
        aiStatus: riskyText ? 'AVAILABLE' : 'NOT_REQUESTED',
        correlationFindings: [],
        correlationStatus: 'NO_MATCH',
      },
      paymentStatus: stop
        ? 'BLOCKED'
        : verify
          ? 'VERIFICATION_REQUIRED'
          : 'PAYPAL_AUTHORIZATION_REQUIRED',
      paypalOrder:
        action === 'ALLOW'
          ? {
              orderId: mockOrderId(),
              approvalUrl:
                'https://www.sandbox.paypal.com/checkoutnow?token=MOCK-DEV-PREVIEW',
            }
          : null,
      verification: verify ? mockVerification(paymentId) : null,
    };
  },

  async capturePayment(orderId: string): Promise<PayPalCaptureResponse> {
    await delay(700);
    if (!orderId.startsWith('MOCK-ORDER-')) {
      throw new Error('Mock checkout can only capture mock orders.');
    }
    return {
      orderId,
      status: 'COMPLETED',
      captureId: `MOCK-CAPTURE-${Date.now()}`,
      amount: 0,
      currency: 'USD',
      paymentStatus: 'COMPLETED',
    };
  },

  async confirmVerification(
    paymentId: string,
    code: string,
  ): Promise<VerificationConfirmResponse> {
    await delay(500);
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
    if (code.trim() === MOCK_VERIFICATION_CODE) {
      mockVerificationAttempts.delete(paymentId);
      return {
        paymentId,
        verificationStatus: 'VERIFIED',
        paymentStatus: 'PAYPAL_AUTHORIZATION_REQUIRED',
        paypalOrder: {
          orderId: mockOrderId(),
          approvalUrl: 'https://www.sandbox.paypal.com/checkoutnow?token=MOCK-DEV-PREVIEW',
        },
        expiresAt,
        attemptsRemaining: MOCK_MAX_ATTEMPTS,
        message: 'Verification complete. Continue to PayPal Sandbox to approve the payment.',
      };
    }
    const attempts = (mockVerificationAttempts.get(paymentId) ?? 0) + 1;
    mockVerificationAttempts.set(paymentId, attempts);
    const remaining = Math.max(0, MOCK_MAX_ATTEMPTS - attempts);
    const failed = remaining === 0;
    if (failed) mockVerificationAttempts.delete(paymentId);
    return {
      paymentId,
      verificationStatus: failed ? 'FAILED' : 'PENDING',
      paymentStatus: 'VERIFICATION_REQUIRED',
      paypalOrder: null,
      expiresAt,
      attemptsRemaining: remaining,
      message: failed
        ? 'Verification failed. Start a new payment attempt.'
        : 'That code did not match. Please try again.',
    };
  },
};
