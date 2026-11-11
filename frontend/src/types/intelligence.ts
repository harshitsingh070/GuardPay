// Global recipient intelligence contracts. Every value comes from the backend;
// identifiers are never exposed and the browser cannot score a recipient.

export interface RecipientReputation {
  status: string;
  reputationStatus: string;
  category: string;
  score: number;
  points: number;
  matchLevel: string;
  identityConfidence: string;
  evidenceCount: number;
  reportCount: number;
  reason: string | null;
}

export interface FraudReportResponse {
  reportId: string;
  recipientReference: string;
  status: string;
  submittedAt: string;
}

export interface RecipientReputationSummary {
  recipientReference: string;
  matchStatus: string;
  reputationStatus: string;
  category: string;
  score: number;
  evidenceCount: number;
  reportCount: number;
  matchLevel: string;
  identityConfidence: string;
  lastEvidenceAt: string | null;
  disclaimer: string;
}

export type FeedbackLabel = 'FRAUD' | 'LEGITIMATE';

export interface FraudFeedback {
  id: string;
  transactionId: string;
  label: string;
  source: string;
  decisionAction: string | null;
  riskScore: number | null;
  createdAt: string;
}
