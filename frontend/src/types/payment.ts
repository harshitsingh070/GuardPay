import type { RecommendedAction, RiskFinding, RiskLevel } from '@/types/risk';

// Guarded payment contracts — business context in, server decision out.
// No risk fields exist on the request: the backend derives every signal.

export interface GuardedPaymentRequest {
  amount: number;
  currency: 'USD';
  description?: string;
  recipientName?: string;
  merchantName?: string;
  deviceId?: string;
  contentText?: string;
  contentUrl?: string;
}

export type GuardedPaymentStatus =
  | 'BLOCKED'
  | 'VERIFICATION_REQUIRED'
  | 'PAYPAL_AUTHORIZATION_REQUIRED';

export interface GuardedPaypalOrder {
  orderId: string;
  approvalUrl: string;
}

export interface GuardedPaymentResponse {
  paymentId: string;
  risk: {
    score: number;
    riskLevel: RiskLevel;
    recommendedAction: RecommendedAction;
    explanation: string;
    automaticSignals: {
      type: string;
      detected: boolean;
      source: string;
      reason: string | null;
      evidence: string;
      severity: string | null;
      confidence: number | null;
    }[];
    findings: RiskFinding[];
    aiStatus: string;
  };
  paymentStatus: GuardedPaymentStatus;
  paypalOrder: GuardedPaypalOrder | null;
}
