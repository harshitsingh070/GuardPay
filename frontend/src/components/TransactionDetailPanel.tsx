import { Loader2, ReceiptText, ShieldAlert } from 'lucide-react';
import CorrelationFindings from '@/components/CorrelationFindings';
import RiskBadge from '@/components/RiskBadge';
import type { TransactionDetail } from '@/types/transaction';
import { classifyFraudPattern } from '@/utils/fraud-pattern';
import { getRiskTheme } from '@/utils/risk-styles';

function money(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
}

function formatDate(value: string | null): string {
  if (value === null) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
}

/** Detail view: what happened and WHY, using the stored backend decision only. */
export default function TransactionDetailPanel({
  detail,
  loading,
  error,
}: {
  detail: TransactionDetail | null;
  loading: boolean;
  error: string | null;
}) {
  const theme = detail?.assessment ? getRiskTheme(detail.assessment.riskLevel) : null;
  const fraudPattern = detail?.assessment
    ? classifyFraudPattern({
        findings: detail.assessment.findings,
        action: detail.assessment.recommendedAction,
        level: detail.assessment.riskLevel,
      })
    : null;

  return (
    <section
      className="gp-card p-5 lg:sticky lg:top-24"
      aria-labelledby="transaction-detail-heading"
      aria-live="polite"
    >
      <div className="flex items-center gap-2.5">
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-[#eaf2ff] text-guardpay-blue">
          <ReceiptText className="h-4 w-4" aria-hidden="true" />
        </span>
        <div>
          <h2 id="transaction-detail-heading" className="text-base font-bold text-guardpay-ink">
            Transaction detail
          </h2>
          <p className="text-xs text-guardpay-muted">Stored server-side decision</p>
        </div>
      </div>

      {loading && (
        <p className="mt-4 flex items-center gap-2 text-sm text-guardpay-muted" role="status">
          <Loader2 className="h-4 w-4 animate-spin text-guardpay-blue" aria-hidden="true" />
          Loading detail…
        </p>
      )}

      {!loading && error !== null && (
        <p className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
          {error}
        </p>
      )}

      {!loading && error === null && detail === null && (
        <div className="mt-5 rounded-xl border border-dashed border-guardpay-line bg-[#f9fafb] px-4 py-8 text-center">
          <ShieldAlert className="mx-auto h-6 w-6 text-guardpay-muted" aria-hidden="true" />
          <p className="mt-2 text-sm text-guardpay-muted">
            Select a transaction to see what GuardPay decided and why.
          </p>
        </div>
      )}

      {!loading && error === null && detail !== null && (
        <div className="mt-4 space-y-4 text-sm">
          <div>
            <p className="text-3xl font-bold tabular-nums tracking-tight text-guardpay-ink">
              {money(detail.amount, detail.currency)}
            </p>
            <div className="mt-1.5 flex flex-wrap items-center gap-2 text-guardpay-muted">
              <span>{detail.recipientName ?? 'No recipient'}</span>
              <span aria-hidden="true">·</span>
              <span className="rounded-full bg-guardpay-canvas px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-guardpay-muted">
                {detail.status}
              </span>
            </div>
            <p className="mt-1 text-xs text-guardpay-muted">
              {formatDate(detail.transactionTimestamp)}
            </p>
          </div>

          <dl className="space-y-2 border-t border-guardpay-line pt-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <dt className="text-guardpay-muted">PayPal order ID</dt>
              <dd className="break-all font-mono text-xs text-guardpay-ink">
                {detail.paypalOrderId ?? '—'}
              </dd>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <dt className="text-guardpay-muted">PayPal capture ID</dt>
              <dd className="break-all font-mono text-xs text-guardpay-ink">
                {detail.paypalCaptureId ?? '—'}
              </dd>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <dt className="text-guardpay-muted">Verification</dt>
              <dd className="font-semibold text-guardpay-ink">
                {detail.verificationStatus ?? '—'}
              </dd>
            </div>
          </dl>

          {detail.assessment !== null && theme !== null ? (
            <div className={`rounded-xl border p-4 ${theme.panel}`}>
              <div className="flex flex-wrap items-center gap-2">
                <ShieldAlert className="h-4 w-4" aria-hidden="true" />
                <span className="font-bold">{detail.assessment.recommendedAction}</span>
                <RiskBadge level={detail.assessment.riskLevel} className="ml-auto" />
              </div>
              <p className="mt-1 text-xs text-guardpay-muted">
                GuardPay score {detail.assessment.score} / 100
              </p>
              {detail.assessment.explanation !== null && (
                <p className="mt-2 text-sm">{detail.assessment.explanation}</p>
              )}
              {fraudPattern !== null && (
                <div className="mt-3 rounded-lg border border-white/70 bg-white/70 p-2.5">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                    Likely fraud type
                  </p>
                  <p className="mt-0.5 font-semibold">{fraudPattern.label}</p>
                  <p className="mt-0.5 text-xs text-guardpay-muted">
                    {fraudPattern.description}
                  </p>
                </div>
              )}
              {detail.assessment.findings.filter((finding) => finding.triggered).length > 0 && (
                <ul className="mt-3 space-y-1.5">
                  {detail.assessment.findings
                    .filter((finding) => finding.triggered)
                    .map((finding) => (
                      <li
                        key={finding.code}
                        className="rounded-lg border border-white/70 bg-white/70 p-2.5"
                      >
                        <p className="font-semibold">
                          {finding.title}
                          {finding.severity !== null && finding.severity !== undefined && (
                            <span className="ml-1.5 rounded-full bg-indigo-100 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-indigo-800">
                              Behavioral
                            </span>
                          )}
                        </p>
                        <p className="text-xs text-guardpay-muted">{finding.description}</p>
                        {finding.evidence !== null && finding.evidence !== undefined && (
                          <p className="mt-1 text-xs text-guardpay-muted">{finding.evidence}</p>
                        )}
                      </li>
                    ))}
                </ul>
              )}
              {detail.assessment.correlationFindings.length > 0 && (
                <CorrelationFindings
                  findings={detail.assessment.correlationFindings}
                  contribution={detail.assessment.correlationContribution}
                  className="mt-3"
                />
              )}
            </div>
          ) : (
            <p className="rounded-xl bg-guardpay-canvas p-3 text-guardpay-muted">
              No stored risk decision for this transaction.
            </p>
          )}
        </div>
      )}
    </section>
  );
}
