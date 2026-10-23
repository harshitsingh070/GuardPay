import type {
  GuardedPaymentRequest,
  GuardedPaymentResponse,
  VerificationConfirmResponse,
} from '@/types/payment';
import type { PayPalCaptureResponse } from '@/types/paypal';

/**
 * Checkout lifecycle. Every transition is driven by a server response or an
 * explicit user action — the browser never invents a payment result.
 */
export type CheckoutPhase =
  | 'READY'
  | 'CHECKING_RISK'
  | 'RISK_ALLOWED'
  | 'RISK_VERIFICATION_REQUIRED'
  | 'VERIFYING'
  | 'RISK_BLOCKED'
  | 'PAYPAL_APPROVAL_REQUIRED'
  | 'CAPTURING'
  | 'COMPLETED'
  | 'FAILED';

export interface CheckoutDraft {
  amount: string;
  recipient: string;
  recipientEmail: string;
  merchant: string;
  description: string;
  url: string;
}

/** Payment data source. The real implementation calls the Spring Boot API. */
export interface CheckoutService {
  createPayment(
    request: GuardedPaymentRequest,
    idempotencyKey: string,
  ): Promise<GuardedPaymentResponse>;
  capturePayment(orderId: string): Promise<PayPalCaptureResponse>;
  confirmVerification(
    paymentId: string,
    code: string,
  ): Promise<VerificationConfirmResponse>;
}

export const EMPTY_DRAFT: CheckoutDraft = {
  amount: '',
  recipient: '',
  recipientEmail: '',
  merchant: '',
  description: '',
  url: '',
};

/** Non-form context that is still part of the attempt's identity. */
export interface CheckoutContext {
  deviceId?: string;
  /** Changes when a new call-state observation is stored. */
  deviceStateKey?: string | null;
}

export function toGuardedPaymentRequest(
  draft: CheckoutDraft,
  deviceId?: string,
): GuardedPaymentRequest {
  return {
    amount: Number(draft.amount),
    currency: 'USD',
    description: 'GuardPay protected payment',
    recipientName: draft.recipient.trim() === '' ? undefined : draft.recipient.trim(),
    recipientEmail:
      draft.recipientEmail.trim() === '' ? undefined : draft.recipientEmail.trim(),
    merchantName: draft.merchant.trim() === '' ? undefined : draft.merchant.trim(),
    deviceId: deviceId === undefined || deviceId.trim() === '' ? undefined : deviceId.trim(),
    contentText: draft.description.trim() === '' ? undefined : draft.description.trim(),
    contentUrl: draft.url.trim() === '' ? undefined : draft.url.trim(),
  };
}

/** Client-side guard only; the backend validates the same fields authoritatively. */
export function validateDraft(draft: CheckoutDraft): string | null {
  const amount = Number(draft.amount);
  if (draft.amount.trim() === '' || Number.isNaN(amount) || amount <= 0) {
    return 'Enter a payment amount greater than zero.';
  }
  if (amount > 100000) {
    return 'This amount exceeds the checkout limit.';
  }
  if (draft.recipientEmail.trim() === '') {
    return "Enter the recipient's PayPal email.";
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.recipientEmail.trim())) {
    return 'Enter a valid recipient PayPal email.';
  }
  return null;
}
