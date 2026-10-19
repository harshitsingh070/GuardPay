import { Link, useLocation } from 'react-router-dom';
import PayPalCheckout from '@/components/PayPalCheckout';
import RiskActionButtons from '@/components/RiskActionButtons';
import RiskFindingList from '@/components/RiskFindingList';
import RiskScore from '@/components/RiskScore';
import RiskWarning from '@/components/RiskWarning';
import { usePageTitle } from '@/hooks/usePageTitle';
import type { RiskEvaluationRequest, RiskEvaluationResponse } from '@/types/risk';
import { riskPanelClass } from '@/utils/risk-styles';

interface RiskLocationState {
  result?: RiskEvaluationResponse;
  request?: RiskEvaluationRequest;
}

function isRiskResponse(value: unknown): value is RiskEvaluationResponse {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.score === 'number' &&
    typeof candidate.riskLevel === 'string' &&
    typeof candidate.recommendedAction === 'string' &&
    typeof candidate.explanation === 'string' &&
    Array.isArray(candidate.findings)
  );
}

export default function RiskPage() {
  usePageTitle('Risk result');
  const location = useLocation();
  const state = location.state as RiskLocationState | null;
  const result = state?.result && isRiskResponse(state.result) ? state.result : null;
  const request = state?.request ?? null;

  if (result === null) {
    return (
      <section className="mx-auto max-w-2xl">
        <h1 className="text-2xl font-semibold">No evaluation yet</h1>
        <p className="mt-2 text-muted-foreground">
          Submit a payment for analysis first — results appear here, never in the URL.
        </p>
        <Link
          to="/payment"
          className="mt-4 inline-block rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary/90 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          Go to payment
        </Link>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-2xl space-y-6" aria-live="polite">
      <RiskWarning level={result.riskLevel} />

      <div className={riskPanelClass(result.riskLevel)}>
        <RiskScore score={result.score} level={result.riskLevel} />
        <div className="mt-4 border-t pt-4">
          <h2 className="text-lg font-semibold">Why GuardPay flagged this</h2>
          <p className="mt-1 text-sm">{result.explanation}</p>
        </div>
      </div>

      <RiskFindingList findings={result.findings} />
      {result.recommendedAction === 'ALLOW' && request !== null ? (
        <PayPalCheckout request={request} />
      ) : (
        <RiskActionButtons recommended={result.recommendedAction} />
      )}

      <Link to="/payment" className="inline-block text-sm font-medium underline">
        ← Back to payment
      </Link>
    </section>
  );
}
