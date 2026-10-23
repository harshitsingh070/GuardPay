import type { RecommendedAction, RiskFinding, RiskLevel } from '@/types/risk';
import type { VerificationStatus } from '@/types/payment';

// Transaction history contracts. Every value is produced by the backend; the
// browser never re-scores a decision or invents a status.

export type TransactionStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface TransactionSummary {
  id: string;
  amount: number;
  currency: string;
  status: TransactionStatus;
  transactionTimestamp: string;
  description: string | null;
  recipientName: string | null;
  merchantName: string | null;
  riskScore: number | null;
  riskLevel: RiskLevel | null;
  recommendedAction: RecommendedAction | null;
}

export interface TransactionPage {
  items: TransactionSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface RiskAssessmentView {
  score: number;
  riskLevel: RiskLevel;
  recommendedAction: RecommendedAction;
  explanation: string | null;
  findings: RiskFinding[];
  createdAt: string | null;
}

export interface TransactionDetail {
  id: string;
  amount: number;
  currency: string;
  status: TransactionStatus;
  description: string | null;
  transactionTimestamp: string;
  createdAt: string | null;
  recipientName: string | null;
  merchantName: string | null;
  paypalOrderId: string | null;
  paypalCaptureId: string | null;
  paypalTransactionId: string | null;
  assessment: RiskAssessmentView | null;
  verificationStatus: VerificationStatus | null;
}

/** History data source. The real implementation calls the Spring Boot API. */
export interface TransactionHistoryService {
  list(page: number, size: number): Promise<TransactionPage>;
  detail(id: string): Promise<TransactionDetail>;
}
