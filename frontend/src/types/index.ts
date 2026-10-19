// Planned shared frontend types (payment context, risk assessment, transaction).
// Types only — no runtime logic. Shapes follow docs/api-contract.md.

export type RiskRecommendation = 'ALLOW' | 'VERIFY' | 'STOP';

export type RiskSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface PaymentAnalysisRequest {
  amount: string;
  currency: string;
  recipient: string;
  merchant: string;
  invoiceText?: string;
  messageText?: string;
  url?: string;
  activeCall: boolean;
}
