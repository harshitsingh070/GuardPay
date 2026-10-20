import { Link, useLocation } from 'react-router-dom';
import { ArrowLeft, ArrowRight, ShieldQuestion } from 'lucide-react';
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
      <section className="mx-auto flex max-w-xl flex-col items-center py-10 text-center sm:py-16">
        <span className="grid h-16 w-16 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
          <ShieldQuestion className="h-8 w-8" aria-hidden="true" />
        </span>
        <p className="gp-eyebrow mt-5">Risk result</p>
        <h1 className="mt-1 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
          No evaluation yet
        </h1>
        <p className="mt-2 max-w-md text-sm leading-relaxed text-guardpay-muted">
          Submit a payment for analysis first — results appear here, never in the URL.
        </p>
        <Link
          to="/payment"
          className="mt-6 inline-flex min-h-12 items-center justify-center gap-2 rounded-full bg-guardpay-blue px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#005ea6] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue"
        >
          Go to payment
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-3xl space-y-5" aria-live="polite">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="gp-eyebrow">Decision result</p>
          <h1 className="mt-0.5 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
            Payment risk result
          </h1>
        </div>
        <Link
          to="/payment"
          className="inline-flex items-center gap-1.5 rounded-full border border-guardpay-line bg-white px-4 py-2 text-sm font-semibold text-guardpay-ink transition-colors hover:border-guardpay-blue/40 hover:bg-guardpay-canvas"
        >
          New evaluation
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </div>

      <RiskWarning level={result.riskLevel} />

      <div className="grid gap-4 sm:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <div className={`${riskPanelClass(result.riskLevel)} flex flex-col justify-center`}>
          <RiskScore score={result.score} level={result.riskLevel} />
        </div>
        <div className="gp-card p-5">
          <h2 className="text-lg font-bold text-guardpay-ink">Why GuardPay flagged this</h2>
          <p className="mt-2 text-sm leading-relaxed text-guardpay-muted">{result.explanation}</p>
          <p className="mt-4 inline-flex items-center rounded-full bg-guardpay-canvas px-3 py-1 text-xs font-semibold text-guardpay-ink">
            Recommended action: {result.recommendedAction}
          </p>
        </div>
      </div>

      <div className="gp-card p-5">
        <RiskFindingList findings={result.findings} />
      </div>

      <div className="gp-card p-5">
        {result.recommendedAction === 'ALLOW' && request !== null ? (
          <PayPalCheckout request={request} />
        ) : (
          <RiskActionButtons recommended={result.recommendedAction} />
        )}
      </div>

      <Link
        to="/payment"
        className="inline-flex items-center gap-1.5 text-sm font-semibold text-guardpay-blue hover:underline"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to payment
      </Link>
    </section>
  );
}
