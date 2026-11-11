import { useState } from 'react';
import { AlertTriangle, CheckCircle2, Flag, Globe2, Loader2 } from 'lucide-react';
import { ApiRequestError, submitFraudReport } from '@/services/api-client';
import type { RecipientReputation } from '@/types/intelligence';

const STATUS_LABEL: Record<string, string> = {
  UNKNOWN: 'No reports',
  REPORTED: 'Reported',
  UNDER_REVIEW: 'Under review',
  CORROBORATED_SUSPICIOUS: 'Corroborated suspicious',
  CONFIRMED_FRAUD: 'Confirmed fraud',
  CLEARED: 'Cleared',
  EXPIRED: 'Expired',
};

const STATUS_STYLE: Record<string, string> = {
  CONFIRMED_FRAUD: 'bg-red-100 text-red-800',
  CORROBORATED_SUSPICIOUS: 'bg-orange-100 text-orange-800',
  UNDER_REVIEW: 'bg-amber-100 text-amber-800',
  REPORTED: 'bg-yellow-100 text-yellow-800',
  CLEARED: 'bg-emerald-100 text-emerald-800',
};

function statusLabel(status: string): string {
  return STATUS_LABEL[status] ?? status.replace(/_/g, ' ').toLowerCase();
}

/**
 * Evidence-only view of the shared recipient reputation store plus a report action.
 * The browser never decides whether a recipient is fraudulent.
 */
export default function RecipientReputationCard({
  reputation,
  transactionId,
  className = '',
}: {
  reputation?: RecipientReputation | null;
  transactionId?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  if (reputation === null || reputation === undefined) return null;

  const unavailable = reputation.status === 'UNAVAILABLE';
  const status = reputation.reputationStatus;
  const badgeStyle = STATUS_STYLE[status] ?? 'bg-slate-100 text-slate-700';

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (transactionId === undefined) return;
    setSubmitting(true);
    setError(null);
    try {
      await submitFraudReport({
        relatedTransactionId: transactionId,
      });
      setSubmitted(true);
      setOpen(false);
    } catch (failure) {
      setError(
        failure instanceof ApiRequestError
          ? failure.message
          : 'The report could not be submitted. Please try again.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={`rounded-xl border border-white/70 bg-white/70 p-3 ${className}`}>
      <div className="flex flex-wrap items-center gap-2">
        <Globe2 className="h-4 w-4 text-guardpay-blue" aria-hidden="true" />
        <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
          Shared recipient reputation
        </p>
        <span
          className={`ml-auto rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${badgeStyle}`}
        >
          {unavailable ? 'Unavailable' : statusLabel(status)}
        </span>
      </div>

      {unavailable ? (
        <p className="mt-2 text-xs text-guardpay-muted">
          The global reputation check could not run for this payment. This is not a clean
          result.
        </p>
      ) : (
        <>
          <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs text-guardpay-muted">
            <span>
              Reports: <span className="font-semibold text-guardpay-ink">{reputation.reportCount}</span>
            </span>
            <span>
              Evidence: <span className="font-semibold text-guardpay-ink">{reputation.evidenceCount}</span>
            </span>
            <span>
              Match: <span className="font-semibold text-guardpay-ink">{reputation.matchLevel}</span>
            </span>
            {reputation.points > 0 && (
              <span>
                Score impact: <span className="font-semibold text-guardpay-ink">+{reputation.points}</span>
              </span>
            )}
          </div>
          <p className="mt-2 text-[11px] text-guardpay-muted">
            Shared evidence only. A report never confirms fraud and never decides this payment
            on its own.
          </p>
        </>
      )}

      {submitted && (
        <p className="mt-2 flex items-center gap-1.5 text-xs font-semibold text-emerald-700" role="status">
          <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
          Report submitted for review.
        </p>
      )}

      {transactionId !== undefined && !unavailable && (
        <div className="mt-3 border-t border-guardpay-line pt-3">
          {!open ? (
            <button
              type="button"
              onClick={() => {
                setOpen(true);
                setError(null);
              }}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-guardpay-blue hover:underline"
            >
              <Flag className="h-3.5 w-3.5" aria-hidden="true" />
              Report this recipient
            </button>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-2">
              <p className="text-xs text-guardpay-muted">
                Reports contain structured evidence only. Do not include names, account numbers,
                phone numbers, or other sensitive identifiers.
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="submit"
                  disabled={submitting}
                  className="inline-flex items-center gap-1.5 rounded-full bg-guardpay-blue px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-60"
                >
                  {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />}
                  Submit report
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setOpen(false);
                    setError(null);
                  }}
                  className="rounded-full border border-guardpay-line px-3 py-1.5 text-xs font-semibold text-guardpay-ink"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}
          {error !== null && (
            <p className="mt-2 flex items-start gap-1.5 text-xs text-red-800" role="alert">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              {error}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
