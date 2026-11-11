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

export type FeedbackType =
  | 'UNAUTHORIZED_PAYMENT'
  | 'SCAM_OR_SOCIAL_ENGINEERING'
  | 'GOODS_NOT_RECEIVED'
  | 'FAKE_INVOICE'
  | 'WRONG_RECIPIENT'
  | 'LEGITIMATE_PAYMENT'
  | 'OTHER'
  | 'FALSE_POSITIVE'
  | 'FALSE_NEGATIVE'
  | 'DISPUTED'
  | 'REFUNDED'
  | 'REVERSED'
  | 'USER_REPORTED';

export type FeedbackStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'VERIFIED_FRAUD'
  | 'VERIFIED_LEGITIMATE'
  | 'REJECTED'
  | 'SUPERSEDED';

export type FeedbackReviewAction = 'START_REVIEW' | 'VERIFY_FRAUD' | 'VERIFY_LEGITIMATE' | 'REJECT' | 'REOPEN';

export interface SubmitFeedbackInput {
  label: FeedbackLabel;
  feedbackType?: FeedbackType;
  reasonCode?: string;
  relatedFindingCode?: string;
  idempotencyKey?: string;
}

export interface FraudFeedback {
  id: string;
  transactionId: string;
  label: string;
  feedbackType: string;
  feedbackStatus: string;
  source: string;
  decisionAction: string | null;
  riskScore: number | null;
  riskAssessmentId: string | null;
  recipientEntityId: string | null;
  relatedFindingCode: string | null;
  reasonCode: string | null;
  reviewerReference: string | null;
  reviewerConfidence: number | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FeedbackPage {
  content: FraudFeedback[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface FeedbackSummary {
  total: number;
  submitted: number;
  underReview: number;
  verifiedFraud: number;
  verifiedLegitimate: number;
  rejected: number;
  superseded: number;
}

export interface FeedbackMetrics {
  total: number;
  fraudLabels: number;
  legitimateLabels: number;
  truePositive: number;
  falsePositive: number;
  trueNegative: number;
  falseNegative: number;
  unclassified: number;
  precision: number | null;
  recall: number | null;
}

export interface LearningDataset {
  id: string;
  datasetVersion: string;
  status: string;
  featureVersion: string;
  labelPolicyVersion: string;
  checksum: string;
  recordCount: number;
  fraudCount: number;
  legitimateCount: number;
  trainCount: number;
  validationCount: number;
  testCount: number;
  excludedCount: number;
  builtAt: string;
  invalidatedAt: string | null;
  invalidationReason: string | null;
  unmetRequirements: string[];
}

export interface LearningModel {
  id: string;
  modelVersion: string;
  datasetId: string;
  datasetVersion: string | null;
  modelType: string;
  status: string;
  verifyThreshold: number | null;
  stopThreshold: number | null;
  precision: number | null;
  recall: number | null;
  f1: number | null;
  accuracy: number | null;
  falsePositiveRate: number | null;
  falseNegativeRate: number | null;
  validationCount: number;
  testCount: number;
  datasetChecksum: string | null;
  labelPolicyVersion: string | null;
  featureVersion: string | null;
  trainingFailureReason: string | null;
  championComparisonJson: string | null;
  rollbackReference: string | null;
  createdAt: string;
  approvedAt: string | null;
  activatedAt: string | null;
  retiredAt: string | null;
}

export interface TrainingResult {
  modelVersion: string;
  status: string;
  trained: boolean;
  failureReason: string | null;
  verifyThreshold: number | null;
  stopThreshold: number | null;
  precision: number | null;
  recall: number | null;
  f1: number | null;
  accuracy: number | null;
  falsePositiveRate: number | null;
  falseNegativeRate: number | null;
  validationCount: number;
  testCount: number;
  championComparisonJson: string | null;
}

export interface LearningEvent {
  eventType: string;
  datasetId: string | null;
  modelId: string | null;
  detail: string | null;
  occurredAt: string;
}

export interface ActiveCalibration {
  modelVersion: string;
  datasetVersion: string | null;
  verifyThreshold: number;
  stopThreshold: number;
  deployed: boolean;
}

export interface LearningOverview {
  totalDatasets: number;
  readyDatasets: number;
  insufficientDatasets: number;
  invalidatedDatasets: number;
  totalModels: number;
  candidateModels: number;
  activeModels: number;
  activeCalibration: ActiveCalibration;
  recentEvents: LearningEvent[];
}
