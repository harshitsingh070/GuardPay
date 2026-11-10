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
  /** Present when the finding came from adaptive behavioral profiling. */
  severity?: string | null;
  confidence?: number | null;
  deviationRatio?: number | null;
  observedValue?: number | null;
  expectedValue?: number | null;
  profileWindow?: string | null;
  contributingFactors?: string[] | null;
  evidence?: string | null;
}

export interface CorrelationFinding {
  correlationCode: string;
  category: string;
  title: string;
  description: string;
  severity: string;
  confidence: number;
  interactionStrength: number;
  contributionPoints: number;
  participatingSignals: string[];
  evidenceFamilies: string[];
  triggeredConditions: string[];
  unavailableConditions: string[];
  ruleVersion: string;
  overlapDiscounted: boolean;
  explanation: string;
}

export interface RiskEvaluationResponse {
  score: number;
  riskLevel: RiskLevel;
  recommendedAction: RecommendedAction;
  explanation: string;
  findings: RiskFinding[];
}
