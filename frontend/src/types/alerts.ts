import type { RecommendedAction } from '@/types/risk';

export type AlertType = 'PAYMENT_RISK' | 'LARGE_CAPTURE';

export type AlertSeverity = 'WARNING' | 'HIGH' | 'CRITICAL';

export interface Alert {
  id: string;
  transactionId: string | null;
  type: AlertType;
  severity: AlertSeverity;
  title: string;
  message: string;
  score: number | null;
  recommendedAction: RecommendedAction | null;
  amount: number | null;
  currency: string | null;
  recipientName: string | null;
  signals: string[];
  createdAt: string;
}
