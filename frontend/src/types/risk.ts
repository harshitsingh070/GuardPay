// GuardPay risk API types. Mirror the Spring Boot response — the backend
// Risk Engine is the ONLY source of truth for scoring. No weights, thresholds,
// or rules are duplicated here.

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type RecommendedAction = 'ALLOW' | 'VERIFY' | 'STOP';

export interface RiskEvaluationRequest {
  amount: number;
  historicalAverage: number | null;
  activeCall: boolean;
  newRecipient: boolean;
  unknownMerchant: boolean;
  unexpectedInvoice: boolean;
  urgentLanguage: boolean;
  suspiciousUrl: boolean;
  socialEngineeringDetected: boolean;
}

export interface RiskFinding {
  code: string;
  title: string;
  description: string;
  points: number;
  triggered: boolean;
}

export interface RiskEvaluationResponse {
  score: number;
  riskLevel: RiskLevel;
  recommendedAction: RecommendedAction;
  explanation: string;
  findings: RiskFinding[];
}
