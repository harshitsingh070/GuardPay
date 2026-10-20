import { AlertCircle, CheckCircle2 } from 'lucide-react';
import type { RiskFinding } from '@/types/risk';

/**
 * Findings straight from the API response (the source of truth — points are
 * displayed, never recalculated). Triggered findings are prominent;
 * non-triggered ones sit in a muted collapsible section.
 */
export default function RiskFindingList({ findings }: { findings: RiskFinding[] }) {
  const triggered = findings.filter((finding) => finding.triggered);
  const calm = findings.filter((finding) => !finding.triggered);

  return (
    <div>
      <h2 className="text-lg font-bold text-guardpay-ink">Risk signals</h2>
      {triggered.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground">No risk signals were triggered.</p>
      ) : (
        <ul className="mt-3 space-y-2" aria-label="Triggered risk signals">
          {triggered.map((finding) => (
            <li
              key={finding.code}
              className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-3.5 shadow-sm"
            >
              <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" aria-hidden="true" />
              <div className="min-w-0 flex-1">
                <p className="font-medium">
                  {finding.title}{' '}
                  <span className="ml-1 rounded-full bg-red-100 px-1.5 py-0.5 text-xs font-bold text-red-800">
                    +{finding.points}
                  </span>
                </p>
                <p className="text-sm text-muted-foreground">{finding.description}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
      {calm.length > 0 && (
        <details className="mt-4 rounded-xl border border-guardpay-line bg-[#f9fafb] p-3.5">
          <summary className="cursor-pointer text-sm font-medium text-guardpay-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue">
            {calm.length} signal{calm.length === 1 ? '' : 's'} not triggered
          </summary>
          <ul className="mt-2 space-y-1.5" aria-label="Signals not triggered">
            {calm.map((finding) => (
              <li key={finding.code} className="flex items-start gap-2 text-sm text-guardpay-muted">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>
                  {finding.title} <span className="text-xs">({finding.code})</span>
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
