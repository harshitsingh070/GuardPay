import type { GuardedPaymentStatus } from '@/types/payment';
import type { RecommendedAction, RiskEvaluationRequest, RiskLevel } from '@/types/risk';

// Backend owns order creation — the frontend never sends credentials, tokens,
// order IDs for creation, or risk verdicts.

export interface CreatePayPalOrderRequest extends RiskEvaluationRequest {
  currency: 'USD';
  description?: string;
}

export interface PayPalOrderResponse {
  orderId: string | null;
  status: string;
  approvalUrl: string | null;
  amount: number;
  currency: string;
  riskLevel: RiskLevel;
  recommendedAction: RecommendedAction;
  allowed: boolean;
  message: string;
}

export interface PayPalCaptureResponse {
  orderId: string;
  status: string;
  captureId: string;
  amount: number;
  currency: string;
  paymentStatus: GuardedPaymentStatus;
}

/** HTTP 403 body when the server-side risk decision blocks capture. */
export interface PayPalGateResponse {
  allowed: false;
  action: RecommendedAction;
  riskLevel: RiskLevel;
  message: string;
  orderId: string;
}
