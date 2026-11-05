import { AlertOctagon, AlertTriangle, ShieldCheck } from 'lucide-react';
import RiskBadge from '@/components/RiskBadge';
import type { GuardedPaymentResponse } from '@/types/payment';
import { classifyFraudPattern } from '@/utils/fraud-pattern';
import { getRiskTheme } from '@/utils/risk-styles';

const COPY = {
  ALLOW: {
    title: 'Payment allowed',
    body: 'No significant risk signals were found. You can continue to PayPal Sandbox checkout.',
    Icon: ShieldCheck,
  },
  VERIFY: {
    title: 'Verification required',
    body: 'Some transaction signals need additional confirmation. GuardPay has not created a PayPal order, and this payment cannot continue until verification succeeds.',
    Icon: AlertTriangle,
  },
  STOP: {
    title: 'Payment blocked',
    body: 'This transaction matched a rule that prevents payment processing. No PayPal order was created and no money moved.',
    Icon: AlertOctagon,
  },
} as const;

/** Explains the real backend decision; never re-scores or invents signals. */
export default function DecisionPanel({ result }: { result: GuardedPaymentResponse }) {
  const action = result.risk.recommendedAction;
  const copy = COPY[action];
  const theme = getRiskTheme(result.risk.riskLevel);
  const triggered = result.risk.findings.filter((finding) => finding.triggered);
  const top = triggered.slice(0, 3);
  const fraudPattern = classifyFraudPattern({
    findings: result.risk.findings,
    action,
    level: result.risk.riskLevel,
  });

  return (
    <section
      className={`rounded-2xl border p-5 shadow-sm ${theme.panel}`}
      aria-live="polite"
      aria-label="GuardPay decision"
    >
      <div className="flex flex-wrap items-center gap-3">
        <copy.Icon className="h-6 w-6 shrink-0" aria-hidden="true" />
        <h2 className="text-xl font-bold">{copy.title}</h2>
        <RiskBadge level={result.risk.riskLevel} className="ml-auto" />
      </div>

      <p className="mt-2 text-sm">{copy.body}</p>
      <p className="mt-2 text-sm text-muted-foreground">{result.risk.explanation}</p>

      {fraudPattern !== null && (
        <div className="mt-4 rounded-xl border border-current/15 bg-background/70 p-3.5">
          <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
            Likely fraud type
          </p>
          <p className="mt-1 text-base font-bold">{fraudPattern.label}</p>
          <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
            {fraudPattern.description}
          </p>
        </div>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
        <span>
          <span className="text-muted-foreground">GuardPay score: </span>
          <span className="font-semibold tabular-nums">{result.risk.score} / 100</span>
        </span>
        <span>
          <span className="text-muted-foreground">AI evidence: </span>
          <span className="font-medium">
            {result.risk.aiStatus === 'AVAILABLE'
              ? 'Used as supporting evidence'
              : result.risk.aiStatus === 'UNAVAILABLE'
                ? 'Unavailable — deterministic signals only'
                : 'Not requested'}
          </span>
        </span>
      </div>

      {top.length > 0 && (
        <div className="mt-4">
          <h3 className="text-sm font-semibold">Signals that contributed</h3>
          <ul className="mt-2 space-y-2">
            {top.map((finding) => (
              <li
                key={finding.code}
                className="flex items-start justify-between gap-3 rounded-lg border bg-background/70 p-3 text-sm"
              >
                <div>
                  <p className="font-medium">{finding.title}</p>
                  <p className="text-xs text-muted-foreground">{finding.description}</p>
                </div>
                <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-xs font-semibold">
                  +{finding.points}
                </span>
              </li>
            ))}
          </ul>
          {triggered.length > top.length && (
            <p className="mt-2 text-xs text-muted-foreground">
              {triggered.length - top.length} more signal
              {triggered.length - top.length === 1 ? '' : 's'} contributed to this result.
            </p>
          )}
        </div>
      )}
    </section>
  );
}
