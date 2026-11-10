import type {
  GuardedPaymentResponse,
  VerificationConfirmResponse,
} from '@/types/payment';
import type { PayPalCaptureResponse } from '@/types/paypal';
import type {
  TransactionDetail,
  TransactionPage,
  TransactionSummary,
} from '@/types/transaction';

export function paymentResponse(
  overrides: Partial<GuardedPaymentResponse> = {},
): GuardedPaymentResponse {
  const base: GuardedPaymentResponse = {
    paymentId: 'pay-1',
    risk: {
      score: 10,
      riskLevel: 'LOW',
      recommendedAction: 'ALLOW',
      explanation: 'No significant risk signals were found for this payment.',
      automaticSignals: [
        {
          type: 'NEW_RECIPIENT',
          detected: false,
          source: 'TRANSACTION_HISTORY',
          reason: null,
          evidence: 'Recipient is known.',
          severity: null,
          confidence: null,
        },
      ],
      findings: [
        {
          code: 'NEW_RECIPIENT',
          title: 'New recipient',
          description: 'This payment is going to a new recipient.',
          points: 20,
          triggered: false,
        },
      ],
      aiStatus: 'NOT_REQUESTED',
      correlationFindings: [],
      correlationStatus: 'NO_MATCH',
    },
    paymentStatus: 'PAYPAL_AUTHORIZATION_REQUIRED',
    paypalOrder: {
      orderId: 'ORDER-1',
      approvalUrl: 'https://www.sandbox.paypal.com/checkoutnow?token=ORDER-1',
    },
    verification: null,
  };
  return {
    ...base,
    ...overrides,
    risk: { ...base.risk, ...(overrides.risk ?? {}) },
  };
}

export function verifyResponse(): GuardedPaymentResponse {
  return paymentResponse({
    risk: {
      score: 45,
      riskLevel: 'MEDIUM',
      recommendedAction: 'VERIFY',
      explanation: 'Some transaction signals need additional confirmation.',
      automaticSignals: [],
      findings: [
        {
          code: 'UNUSUAL_AMOUNT',
          title: 'Unusually large amount',
          description: 'Payment amount is significantly higher than normal.',
          points: 20,
          triggered: true,
        },
      ],
      aiStatus: 'AVAILABLE',
      correlationFindings: [],
      correlationStatus: 'NO_MATCH',
    },
    paymentStatus: 'VERIFICATION_REQUIRED',
    paypalOrder: null,
    verification: {
      verificationId: 'ver-1',
      status: 'PENDING',
      expiresAt: '2099-01-01T00:00:00Z',
      attemptsRemaining: 3,
      simulated: true,
      channelHint: 'Test channel.',
    },
  });
}

export function verifiedConfirmation(
  overrides: Partial<VerificationConfirmResponse> = {},
): VerificationConfirmResponse {
  return {
    paymentId: 'pay-1',
    verificationStatus: 'VERIFIED',
    paymentStatus: 'PAYPAL_AUTHORIZATION_REQUIRED',
    paypalOrder: {
      orderId: 'ORDER-VERIFIED',
      approvalUrl: 'https://www.sandbox.paypal.com/checkoutnow?token=ORDER-VERIFIED',
    },
    expiresAt: '2099-01-01T00:00:00Z',
    attemptsRemaining: 3,
    message: 'Verification complete.',
    ...overrides,
  };
}

export function pendingConfirmation(
  overrides: Partial<VerificationConfirmResponse> = {},
): VerificationConfirmResponse {
  return {
    paymentId: 'pay-1',
    verificationStatus: 'PENDING',
    paymentStatus: 'VERIFICATION_REQUIRED',
    paypalOrder: null,
    expiresAt: '2099-01-01T00:00:00Z',
    attemptsRemaining: 2,
    message: 'That code did not match. Please try again.',
    ...overrides,
  };
}

export function failedConfirmation(
  overrides: Partial<VerificationConfirmResponse> = {},
): VerificationConfirmResponse {
  return {
    paymentId: 'pay-1',
    verificationStatus: 'FAILED',
    paymentStatus: 'VERIFICATION_REQUIRED',
    paypalOrder: null,
    expiresAt: '2099-01-01T00:00:00Z',
    attemptsRemaining: 0,
    message: 'Verification failed. Start a new payment attempt.',
    ...overrides,
  };
}

export function blockedResponse(): GuardedPaymentResponse {
  return paymentResponse({
    risk: {
      score: 100,
      riskLevel: 'CRITICAL',
      recommendedAction: 'STOP',
      explanation: 'GuardPay flagged several high-risk signals.',
      automaticSignals: [],
      findings: [
        {
          code: 'SOCIAL_ENGINEERING',
          title: 'Known scam pattern',
          description: 'Behaviour matches a known scam pattern.',
          points: 20,
          triggered: true,
        },
      ],
      aiStatus: 'AVAILABLE',
      correlationFindings: [],
      correlationStatus: 'NO_MATCH',
    },
    paymentStatus: 'BLOCKED',
    paypalOrder: null,
  });
}

export function completedCapture(orderId = 'ORDER-1'): PayPalCaptureResponse {
  return {
    orderId,
    status: 'COMPLETED',
    captureId: 'CAPTURE-1',
    amount: 45,
    currency: 'USD',
    paymentStatus: 'COMPLETED',
  };
}

export function transactionSummary(
  overrides: Partial<TransactionSummary> = {},
): TransactionSummary {
  return {
    id: 'txn-1',
    amount: 45,
    currency: 'USD',
    status: 'COMPLETED',
    transactionTimestamp: '2026-01-01T10:00:00Z',
    description: 'Invoice 1042',
    recipientName: 'Known Co',
    merchantName: null,
    riskScore: 60,
    riskLevel: 'MEDIUM',
    recommendedAction: 'VERIFY',
    ...overrides,
  };
}

export function transactionPage(
  overrides: Partial<TransactionPage> = {},
): TransactionPage {
  const items = overrides.items ?? [transactionSummary()];
  return {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: 1,
    ...overrides,
  };
}

export function transactionDetail(
  overrides: Partial<TransactionDetail> = {},
): TransactionDetail {
  return {
    id: 'txn-1',
    amount: 45,
    currency: 'USD',
    status: 'COMPLETED',
    description: 'Invoice 1042',
    transactionTimestamp: '2026-01-01T10:00:00Z',
    createdAt: '2026-01-01T10:00:00Z',
    recipientName: 'Known Co',
    merchantName: null,
    paypalOrderId: 'ORDER-1',
    paypalCaptureId: 'CAPTURE-1',
    paypalTransactionId: null,
    assessment: {
      score: 60,
      riskLevel: 'MEDIUM',
      recommendedAction: 'VERIFY',
      explanation: 'Some transaction signals need additional confirmation.',
      findings: [
        {
          code: 'NEW_RECIPIENT',
          title: 'New recipient',
          description: 'First payment to this recipient.',
          points: 20,
          triggered: true,
        },
      ],
      createdAt: '2026-01-01T10:00:00Z',
      correlationFindings: [],
      correlationStatus: 'NO_MATCH',
      correlationContribution: 0,
    },
    verificationStatus: 'VERIFIED',
    ...overrides,
  };
}

export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}
