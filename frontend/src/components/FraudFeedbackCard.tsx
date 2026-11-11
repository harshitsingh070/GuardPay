import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Loader2, ThumbsDown, ThumbsUp } from 'lucide-react';
import {
  ApiRequestError,
  getFraudFeedback,
  submitFraudFeedback,
} from '@/services/api-client';
import type { FeedbackLabel, FraudFeedback } from '@/types/intelligence';

/**
 * Explicit outcome feedback for one transaction. Labels are stored server-side
 * and exported for offline evaluation; they never change a live decision.
 */
export default function FraudFeedbackCard({
  transactionId,
  className = '',
}: {
  transactionId: string;
  className?: string;
}) {
  const [feedback, setFeedback] = useState<FraudFeedback | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getFraudFeedback(transactionId)
      .then((value) => {
        if (!cancelled) setFeedback(value);
      })
      .catch(() => {
        if (!cancelled) setFeedback(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [transactionId]);

  async function record(label: FeedbackLabel) {
    setSubmitting(true);
    setError(null);
    try {
      setFeedback(await submitFraudFeedback(transactionId, { label }));
    } catch (failure) {
      setError(
        failure instanceof ApiRequestError
          ? failure.message
          : 'Feedback could not be saved. Please try again.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={`rounded-xl border border-white/70 bg-white/70 p-3 ${className}`}>
      <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
        Was this decision right?
      </p>
      <p className="mt-1 text-xs text-guardpay-muted">
        Your label is used to evaluate and retrain GuardPay. It does not change this payment.
      </p>

      {loading ? (
        <p className="mt-2 flex items-center gap-1.5 text-xs text-guardpay-muted">
          <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
          Loading feedback…
        </p>
      ) : (
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <button
            type="button"
            disabled={submitting}
            onClick={() => record('FRAUD')}
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold disabled:opacity-60 ${
              feedback?.label === 'FRAUD'
                ? 'bg-red-700 text-white'
                : 'border border-guardpay-line text-guardpay-ink'
            }`}
          >
            <ThumbsDown className="h-3.5 w-3.5" aria-hidden="true" />
            This was fraud
          </button>
          <button
            type="button"
            disabled={submitting}
            onClick={() => record('LEGITIMATE')}
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold disabled:opacity-60 ${
              feedback?.label === 'LEGITIMATE'
                ? 'bg-emerald-700 text-white'
                : 'border border-guardpay-line text-guardpay-ink'
            }`}
          >
            <ThumbsUp className="h-3.5 w-3.5" aria-hidden="true" />
            This was legitimate
          </button>
          {submitting && (
            <Loader2 className="h-3.5 w-3.5 animate-spin text-guardpay-muted" aria-hidden="true" />
          )}
        </div>
      )}

      {feedback !== null && (
        <p
          className="mt-2 flex items-center gap-1.5 text-xs font-semibold text-emerald-700"
          role="status"
        >
          <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
          Recorded as {feedback.label === 'FRAUD' ? 'fraud' : 'legitimate'}
          {feedback.feedbackStatus ? ` (${feedback.feedbackStatus.replace(/_/g, ' ')})` : ''}.
        </p>
      )}
      {error !== null && (
        <p className="mt-2 flex items-start gap-1.5 text-xs text-red-800" role="alert">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          {error}
        </p>
      )}
    </div>
  );
}
