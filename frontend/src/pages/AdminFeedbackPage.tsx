import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Loader2, MessageSquareWarning, RefreshCw, ShieldCheck } from 'lucide-react';
import { usePageTitle } from '@/hooks/usePageTitle';
import {
  ApiRequestError,
  getFeedbackMetrics,
  getFeedbackSummary,
  listAdminFeedback,
  reviewFeedback,
} from '@/services/api-client';
import type {
  FeedbackMetrics,
  FeedbackReviewAction,
  FeedbackSummary,
  FraudFeedback,
} from '@/types/intelligence';

const STATUS_OPTIONS = [
  '',
  'SUBMITTED',
  'UNDER_REVIEW',
  'VERIFIED_FRAUD',
  'VERIFIED_LEGITIMATE',
  'REJECTED',
  'SUPERSEDED',
];

function messageOf(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message;
  return 'The feedback queue could not be loaded. Admin access is required.';
}

function Label({ value }: { value: string }) {
  const fraud = value === 'VERIFIED_FRAUD' || value === 'FRAUD';
  const legitimate = value === 'VERIFIED_LEGITIMATE' || value === 'LEGITIMATE';
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
        fraud
          ? 'bg-red-100 text-red-800'
          : legitimate
            ? 'bg-emerald-100 text-emerald-800'
            : 'bg-guardpay-canvas text-guardpay-muted'
      }`}
    >
      {value.replace(/_/g, ' ')}
    </span>
  );
}

/** Admin-only verification queue. Verification is the only path into a dataset. */
export default function AdminFeedbackPage() {
  usePageTitle('Feedback review');
  const [status, setStatus] = useState('');
  const [rows, setRows] = useState<FraudFeedback[]>([]);
  const [summary, setSummary] = useState<FeedbackSummary | null>(null);
  const [metrics, setMetrics] = useState<FeedbackMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [page, nextSummary, nextMetrics] = await Promise.all([
        listAdminFeedback(status === '' ? null : status),
        getFeedbackSummary(),
        getFeedbackMetrics(),
      ]);
      setRows(page.content);
      setSummary(nextSummary);
      setMetrics(nextMetrics);
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  async function review(id: string, action: FeedbackReviewAction) {
    setBusy(`${action}-${id}`);
    setError(null);
    setNotice(null);
    try {
      await reviewFeedback(id, action);
      setNotice(`Feedback ${id.slice(0, 8)} updated: ${action.replace(/_/g, ' ')}.`);
      await load();
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
            <MessageSquareWarning className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="gp-eyebrow">Admin</p>
            <h1 className="mt-0.5 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
              Feedback review
            </h1>
            <p className="mt-1 max-w-2xl text-sm text-guardpay-muted">
              Only admin-verified labels can enter a learning dataset. User reports are evidence,
              never ground truth on their own.
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            className="rounded-full border border-guardpay-line bg-white px-3 py-2.5 text-sm text-guardpay-ink"
            aria-label="Filter by status"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option === '' ? 'All statuses' : option.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => void load()}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-full border border-guardpay-line bg-white px-4 py-2.5 text-sm font-semibold text-guardpay-ink"
          >
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Refresh
          </button>
        </div>
      </div>

      {loading && (
        <div className="gp-card mt-6 flex items-center gap-3 p-6" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-guardpay-blue" aria-hidden="true" />
          <span className="text-sm text-guardpay-muted">Loading feedback queue…</span>
        </div>
      )}

      {!loading && error !== null && (
        <div className="mt-6 rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-800" role="alert">
          <p className="flex items-center gap-2 font-semibold">
            <AlertTriangle className="h-4 w-4" aria-hidden="true" />
            Feedback request failed
          </p>
          <p className="mt-1">{error}</p>
        </div>
      )}

      {notice !== null && (
        <div className="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900" role="status">
          {notice}
        </div>
      )}

      {!loading && summary !== null && metrics !== null && (
        <div className="gp-card mt-6 grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Verified labels
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-guardpay-ink">
              {summary.verifiedFraud + summary.verifiedLegitimate}
            </p>
            <p className="text-xs text-guardpay-muted">
              {summary.verifiedFraud} fraud · {summary.verifiedLegitimate} legitimate
            </p>
          </div>
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Awaiting review
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-guardpay-ink">
              {summary.submitted + summary.underReview}
            </p>
            <p className="text-xs text-guardpay-muted">
              {summary.submitted} submitted · {summary.underReview} in review
            </p>
          </div>
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Decision precision
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-guardpay-ink">
              {metrics.precision === null ? '—' : `${(metrics.precision * 100).toFixed(1)}%`}
            </p>
            <p className="text-xs text-guardpay-muted">
              recall {metrics.recall === null ? '—' : `${(metrics.recall * 100).toFixed(1)}%`}
            </p>
          </div>
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Flagged errors
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-guardpay-ink">
              {metrics.falsePositive + metrics.falseNegative}
            </p>
            <p className="text-xs text-guardpay-muted">
              {metrics.falsePositive} false positive · {metrics.falseNegative} false negative
            </p>
          </div>
        </div>
      )}

      {!loading && (
        <div className="gp-card mt-6 overflow-hidden">
          <div className="border-b border-guardpay-line px-4 py-3.5">
            <h2 className="text-sm font-bold text-guardpay-ink">Queue</h2>
            <p className="text-xs text-guardpay-muted">
              Verification writes an audit event and may invalidate datasets that used the label.
            </p>
          </div>
          {rows.length === 0 ? (
            <p className="px-4 py-10 text-center text-sm text-guardpay-muted">
              No feedback matches this filter.
            </p>
          ) : (
            <div className="divide-y divide-guardpay-line">
              {rows.map((row) => (
                <div key={row.id} className="grid gap-3 px-4 py-4 lg:grid-cols-[1fr_auto]">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <Label value={row.label} />
                      <Label value={row.feedbackStatus} />
                      <span className="text-[10px] font-semibold uppercase tracking-wider text-guardpay-muted">
                        {row.feedbackType.replace(/_/g, ' ')}
                      </span>
                    </div>
                    <p className="mt-1.5 text-sm text-guardpay-ink">
                      Decision {row.decisionAction ?? 'unknown'} · score {row.riskScore ?? '—'} ·
                      source {row.source}
                    </p>
                    <p className="mt-0.5 font-mono text-[10px] text-guardpay-muted">
                      transaction {row.transactionId}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-start gap-2">
                    <button
                      type="button"
                      disabled={busy !== null}
                      onClick={() => void review(row.id, 'VERIFY_FRAUD')}
                      className="rounded-full bg-red-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                    >
                      Verify fraud
                    </button>
                    <button
                      type="button"
                      disabled={busy !== null}
                      onClick={() => void review(row.id, 'VERIFY_LEGITIMATE')}
                      className="rounded-full bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                    >
                      Verify legitimate
                    </button>
                    {row.feedbackStatus !== 'SUBMITTED' &&
                      row.feedbackStatus !== 'UNDER_REVIEW' && (
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() => void review(row.id, 'REOPEN')}
                          className="rounded-full border border-guardpay-line px-3 py-1.5 text-xs font-semibold text-guardpay-ink disabled:opacity-50"
                        >
                          Reopen
                        </button>
                      )}
                    {(row.feedbackStatus === 'SUBMITTED' ||
                      row.feedbackStatus === 'UNDER_REVIEW') && (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() => void review(row.id, 'REJECT')}
                        className="rounded-full border border-guardpay-line px-3 py-1.5 text-xs font-semibold text-guardpay-ink disabled:opacity-50"
                      >
                        Reject
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <p className="mt-4 flex items-center gap-1.5 text-xs text-guardpay-muted">
        <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
        Verification never deploys a model. Dataset build and deployment stay separate admin steps.
      </p>
    </section>
  );
}
