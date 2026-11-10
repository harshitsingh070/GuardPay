import { GitMerge } from 'lucide-react';
import type { CorrelationFinding } from '@/types/risk';
import { cn } from '@/utils/cn';

function severityClass(severity: string): string {
  switch (severity.toUpperCase()) {
    case 'CRITICAL':
    case 'HIGH':
      return 'bg-red-100 text-red-800';
    case 'MEDIUM':
      return 'bg-amber-100 text-amber-800';
    default:
      return 'bg-slate-100 text-slate-700';
  }
}

/**
 * Correlation evidence produced by the backend. Points are displayed exactly
 * as returned; this component never re-scores a payment.
 */
export default function CorrelationFindings({
  findings,
  contribution,
  className,
}: {
  findings: CorrelationFinding[];
  contribution?: number;
  className?: string;
}) {
  if (findings.length === 0) return null;
  const total = contribution ?? findings.reduce((sum, finding) => sum + finding.contributionPoints, 0);

  return (
    <div className={cn('rounded-xl border border-indigo-200 bg-indigo-50/60 p-3.5', className)}>
      <div className="flex flex-wrap items-center gap-2">
        <GitMerge className="h-4 w-4 shrink-0 text-indigo-700" aria-hidden="true" />
        <h3 className="text-sm font-bold text-indigo-900">Signal correlation</h3>
        <span className="ml-auto rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-indigo-800">
          +{total} risk points
        </span>
      </div>
      <p className="mt-1 text-xs text-indigo-900/80">
        Combinations of signals that are suspicious together even when each one is weak on its own.
      </p>
      <ul className="mt-2.5 space-y-2" aria-label="Correlated signal combinations">
        {findings.map((finding) => (
          <li
            key={finding.correlationCode}
            className="rounded-lg border border-indigo-200 bg-white/80 p-2.5"
          >
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-sm font-semibold text-guardpay-ink">{finding.title}</span>
              <span
                className={cn(
                  'rounded-full px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide',
                  severityClass(finding.severity),
                )}
              >
                {finding.severity}
              </span>
              <span className="ml-auto rounded bg-indigo-100 px-1.5 py-0.5 text-xs font-semibold text-indigo-800">
                +{finding.contributionPoints}
              </span>
            </div>
            <p className="mt-1 text-xs text-guardpay-muted">{finding.description}</p>
            {finding.participatingSignals.length > 0 && (
              <p className="mt-1.5 flex flex-wrap gap-1">
                {finding.participatingSignals.map((signal) => (
                  <span
                    key={signal}
                    className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] text-slate-700"
                  >
                    {signal}
                  </span>
                ))}
              </p>
            )}
            {finding.overlapDiscounted && (
              <p className="mt-1 text-[11px] italic text-guardpay-muted">
                Overlap with a stronger correlation was discounted.
              </p>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
