import { TrendingUp } from 'lucide-react';
import type { RiskFinding } from '@/types/risk';
import { getHighValueFindings, highValueClassificationLabel } from '@/utils/high-value';

const SEVERITY_STYLE: Record<string, string> = {
  CRITICAL: 'bg-red-100 text-red-800',
  HIGH: 'bg-orange-100 text-orange-800',
  MEDIUM: 'bg-amber-100 text-amber-800',
  LOW: 'bg-slate-100 text-slate-700',
};

function formatAmount(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value);
}

function formatRatio(value: number): string {
  return `${value.toFixed(2)}×`;
}

/**
 * Explains the backend's first-time high-value evidence without re-scoring.
 * Amounts are compared only with the user's own history in the same currency.
 */
export default function FirstTimeHighValueCard({
  findings,
  className = '',
}: {
  findings: RiskFinding[];
  className?: string;
}) {
  const items = getHighValueFindings(findings);
  if (items.length === 0) return null;

  return (
    <section
      className={`rounded-xl border border-indigo-200 bg-indigo-50/70 p-3.5 ${className}`}
      aria-label="First-time high-value payment evidence"
    >
      <div className="flex flex-wrap items-center gap-2">
        <TrendingUp className="h-4 w-4 text-indigo-700" aria-hidden="true" />
        <p className="text-[11px] font-bold uppercase tracking-wider text-indigo-800">
          First-time high-value payment
        </p>
      </div>
      <p className="mt-1 text-xs leading-relaxed text-indigo-900/80">
        Compared only with your own completed payments in the same currency. A new recipient is
        treated as unverified, not automatically fraudulent.
      </p>

      <ul className="mt-3 space-y-2">
        {items.map((finding) => {
          const evidence =
            finding.evidence !== null &&
            finding.evidence !== undefined &&
            finding.evidence !== finding.description
              ? finding.evidence
              : null;
          const severityStyle =
            SEVERITY_STYLE[finding.severity ?? ''] ?? 'bg-indigo-100 text-indigo-800';

          return (
            <li
              key={finding.code}
              className="rounded-lg border border-indigo-200/70 bg-white/80 p-2.5"
            >
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-sm font-semibold text-guardpay-ink">
                  {finding.title || highValueClassificationLabel(finding.code)}
                </p>
                {finding.severity !== null && finding.severity !== undefined && (
                  <span
                    className={`ml-auto rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${severityStyle}`}
                  >
                    {finding.severity}
                  </span>
                )}
              </div>
              <p className="mt-0.5 text-xs text-guardpay-muted">{finding.description}</p>
              {evidence !== null && (
                <p className="mt-1 text-xs text-guardpay-muted">{evidence}</p>
              )}
              <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-guardpay-muted">
                {finding.deviationRatio !== null && finding.deviationRatio !== undefined && (
                  <span>
                    Ratio:{' '}
                    <span className="font-semibold text-guardpay-ink">
                      {formatRatio(finding.deviationRatio)}
                    </span>
                  </span>
                )}
                {finding.observedValue !== null && finding.observedValue !== undefined && (
                  <span>
                    Amount:{' '}
                    <span className="font-semibold text-guardpay-ink">
                      {formatAmount(finding.observedValue)}
                    </span>
                  </span>
                )}
                {finding.expectedValue !== null && finding.expectedValue !== undefined && (
                  <span>
                    Your baseline:{' '}
                    <span className="font-semibold text-guardpay-ink">
                      {formatAmount(finding.expectedValue)}
                    </span>
                  </span>
                )}
                {finding.profileWindow !== null && finding.profileWindow !== undefined && (
                  <span>
                    Window:{' '}
                    <span className="font-semibold text-guardpay-ink">
                      {finding.profileWindow}
                    </span>
                  </span>
                )}
                {finding.confidence !== null && finding.confidence !== undefined && (
                  <span>
                    Confidence:{' '}
                    <span className="font-semibold text-guardpay-ink">
                      {Math.round(finding.confidence * 100)}%
                    </span>
                  </span>
                )}
                {finding.points > 0 && (
                  <span>
                    Score impact:{' '}
                    <span className="font-semibold text-guardpay-ink">+{finding.points}</span>
                  </span>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
