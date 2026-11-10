import type {
  CorrelationFinding,
  RecommendedAction,
  RiskFinding,
  RiskLevel,
} from '@/types/risk';
import type { RecipientReputation } from '@/types/intelligence';

// Guarded payment contracts — business context in, server decision out.
// No risk fields exist on the request: the backend derives every signal.

export interface GuardedPaymentRequest {
  amount: number;
  currency: 'USD';
  description?: string;
  recipientName?: string;
  recipientEmail?: string;
  recipientPaypalAccountId?: string;
  merchantName?: string;
  deviceId?: string;
  deviceFingerprint?: string;
  contentText?: string;
  contentUrl?: string;
}

export type GuardedPaymentStatus =
  | 'BLOCKED'
  | 'VERIFICATION_REQUIRED'
  | 'PAYPAL_AUTHORIZATION_REQUIRED'
  | 'PENDING'
  | 'APPROVED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED';

export interface GuardedPaypalOrder {
  orderId: string;
  approvalUrl: string;
}

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'FAILED' | 'EXPIRED';

/**
 * Controlled verification challenge. The backend issues it for a VERIFY
 * decision and alone records the outcome; the browser only supplies an answer.
 * The code is delivered out of band and is never part of this response.
 */
export interface GuardedVerification {
  verificationId: string;
  status: VerificationStatus;
  expiresAt: string;
  attemptsRemaining: number;
  simulated: boolean;
  channelHint: string;
}

export interface VerificationConfirmResponse {
  paymentId: string;
  verificationStatus: VerificationStatus;
  paymentStatus: GuardedPaymentStatus;
  paypalOrder: GuardedPaypalOrder | null;
  expiresAt: string;
  attemptsRemaining: number;
  message: string;
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
    behavioralProfileStatus?: string;
    correlationFindings: CorrelationFinding[];
    correlationStatus: string;
    recipientReputation?: RecipientReputation | null;
  };
  paymentStatus: GuardedPaymentStatus;
  paypalOrder: GuardedPaypalOrder | null;
  verification: GuardedVerification | null;
}
