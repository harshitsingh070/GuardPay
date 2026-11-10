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

export interface BehavioralProfile {
  userId: string;
  profileStatus: string;
  transactionCount: number | null;
  profileLastUpdated: string | null;
  dataQualityScore: string | null;
  amountBehavior: BehavioralAmountBehavior | null;
  frequencyBehavior: BehavioralFrequencyBehavior | null;
  timeBehavior: BehavioralTimeBehavior | null;
  recipientBehavior: BehavioralRecipientBehavior | null;
  locationBehavior: BehavioralLocationBehavior | null;
  deviceBehavior: BehavioralDeviceBehavior | null;
  behavioralDimensions: BehavioralDimensionStatus[];
}

export interface BehavioralAmountBehavior {
  availability: string;
  medianAmount: number | null;
  typicalMin: number | null;
  typicalMax: number | null;
  p90Amount: number | null;
  p95Amount: number | null;
}

export interface BehavioralFrequencyBehavior {
  availability: string;
  paymentsPerDay: number | null;
  paymentsPerWeek: number | null;
  avgIntervalMinutes: number | null;
}

export interface BehavioralTimeBehavior {
  availability: string;
  peakHour: number | null;
  weekendPaymentRatio: number | null;
  nightPaymentRatio: number | null;
  typicalHours: number[];
}

export interface BehavioralRecipientBehavior {
  availability: string;
  uniqueRecipientCount: number | null;
  establishedRecipientCount: number | null;
  newRecipientRatio: number | null;
  medianRecipientAmount: number | null;
}

export interface BehavioralLocationBehavior {
  availability: string;
  geoCountryCount: number | null;
  typicalCountries: string[];
}

export interface BehavioralDeviceBehavior {
  availability: string;
  deviceCount: number | null;
  familiarDeviceFingerprints: string[];
}

export interface BehavioralDimensionStatus {
  dimension: string;
  status: string;
  reason: string;
}

export interface BehavioralProfileSummary {
  profileStatus: string;
  transactionCount: number | null;
  profileLastUpdated: string | null;
  normalAmountRange: string;
  typicalPaymentHours: string;
  recipientFamiliarity: string;
  deviceFamiliarity: string;
  recentAnomalies: string[];
}

/** Currency-specific spending baseline used by the high-value engine. */
export interface HighValueBaseline {
  currency: string;
  profileStatus: string;
  transactionCount: number;
  medianAmount: number | null;
  madAmount: number | null;
  historicalMax: number | null;
  p75Amount: number | null;
  p90Amount: number | null;
  p95Amount: number | null;
  typicalHighValueThreshold: number | null;
  established: boolean;
}
