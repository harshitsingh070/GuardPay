import type { RiskFinding } from '@/types/risk';

/**
 * Presentation-only helpers for first-time high-value findings. The backend
 * engine owns the baseline, thresholds, scoring, and decision; the browser
 * only groups and labels the evidence it returned.
 */
export const HIGH_VALUE_SIGNAL_CODES = new Set([
  'FIRST_HIGH_VALUE_PAYMENT',
  'FIRST_HIGH_VALUE_TO_RECIPIENT',
  'FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE',
]);

const CLASSIFICATION_LABEL: Record<string, string> = {
  FIRST_HIGH_VALUE_PAYMENT: 'First high-value payment',
  FIRST_HIGH_VALUE_TO_RECIPIENT: 'First high-value payment to recipient',
  FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE: 'First payment to recipient is high value',
};

export function isHighValueFinding(finding: RiskFinding): boolean {
  return HIGH_VALUE_SIGNAL_CODES.has(finding.code);
}

/** Triggered high-value findings only, preserving the backend's order. */
export function getHighValueFindings(findings: RiskFinding[]): RiskFinding[] {
  return findings.filter((finding) => finding.triggered && isHighValueFinding(finding));
}

export function highValueClassificationLabel(code: string): string {
  return CLASSIFICATION_LABEL[code] ?? 'First-time high-value payment';
}
