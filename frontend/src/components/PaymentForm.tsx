import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { ApiRequestError, evaluateRisk } from '@/services/api-client';
import type { RiskEvaluationRequest, RiskEvaluationResponse } from '@/types/risk';
import { cn } from '@/utils/cn';

interface SignalToggle {
  key:
    | 'activeCall'
    | 'newRecipient'
    | 'unknownMerchant'
    | 'unexpectedInvoice'
    | 'urgentLanguage'
    | 'suspiciousUrl'
    | 'socialEngineeringDetected';
  label: string;
}

const SIGNAL_TOGGLES: SignalToggle[] = [
  { key: 'activeCall', label: 'Active phone call' },
  { key: 'newRecipient', label: 'New recipient' },
  { key: 'unknownMerchant', label: 'Unknown merchant' },
  { key: 'unexpectedInvoice', label: 'Unexpected invoice' },
  { key: 'urgentLanguage', label: 'Urgent language' },
  { key: 'suspiciousUrl', label: 'Suspicious URL' },
  { key: 'socialEngineeringDetected', label: 'Social engineering detected' },
];

const inputClass =
  'w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring';

/**
 * Payment evaluation form. Sends inputs to the backend — no scoring here.
 * Recipient/merchant are context only in this milestone (the evaluation API
 * scores amount, history, and risk signals).
 */
export default function PaymentForm({
  onResult,
}: {
  onResult: (result: RiskEvaluationResponse, request: RiskEvaluationRequest) => void;
}) {
  const [amount, setAmount] = useState('');
  const [historicalAverage, setHistoricalAverage] = useState('87');
  const [recipient, setRecipient] = useState('');
  const [merchant, setMerchant] = useState('');
  const [signals, setSignals] = useState<Record<SignalToggle['key'], boolean>>({
    activeCall: false,
    newRecipient: false,
    unknownMerchant: false,
    unexpectedInvoice: false,
    urgentLanguage: false,
    suspiciousUrl: false,
    socialEngineeringDetected: false,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const parsedAmount = Number(amount);
    if (amount.trim() === '' || Number.isNaN(parsedAmount)) {
      setError('Please enter a payment amount.');
      return;
    }
    if (parsedAmount < 0) {
      setError('Amount must be zero or more.');
      return;
    }
    let average: number | null = null;
    if (historicalAverage.trim() !== '') {
      const parsedAverage = Number(historicalAverage);
      if (Number.isNaN(parsedAverage) || parsedAverage < 0) {
        setError('Historical average must be zero or more, or left blank.');
        return;
      }
      average = parsedAverage;
    }

    setSubmitting(true);
    setError(null);
    try {
      const evaluation: RiskEvaluationRequest = {
        amount: parsedAmount,
        historicalAverage: average,
        ...signals,
      };
      const result = await evaluateRisk(evaluation);
      onResult(result, evaluation);
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.status === 400 ? err.message : 'Unable to analyze this payment. Please try again.');
      } else {
        setError('Unable to analyze this payment. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-busy={submitting} className="space-y-6">
      <section aria-labelledby="payment-details-heading" className="rounded-lg border p-4 sm:p-6">
        <h2 id="payment-details-heading" className="text-lg font-semibold">
          Payment details
        </h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-3">
          <div>
            <label htmlFor="amount" className="mb-1 block text-sm font-medium">
              Amount ($)
            </label>
            <input
              id="amount"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              required
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="2499.00"
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="recipient" className="mb-1 block text-sm font-medium">
              Recipient
            </label>
            <input
              id="recipient"
              type="text"
              value={recipient}
              onChange={(event) => setRecipient(event.target.value)}
              placeholder="recipient@example.com"
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="merchant" className="mb-1 block text-sm font-medium">
              Merchant
            </label>
            <input
              id="merchant"
              type="text"
              value={merchant}
              onChange={(event) => setMerchant(event.target.value)}
              placeholder="Merchant name"
              className={inputClass}
            />
          </div>
        </div>
        <p className="mt-2 text-xs text-muted-foreground">
          Recipient and merchant are collected for context — they are not sent to the
          evaluation API in this milestone.
        </p>
      </section>

      <section aria-labelledby="risk-context-heading" className="rounded-lg border p-4 sm:p-6">
        <h2 id="risk-context-heading" className="text-lg font-semibold">
          Risk context
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Demo risk signals — the browser is not actually detecting phone calls. These
          switches simulate the context sent with the payment.
        </p>
        <fieldset className="mt-4">
          <legend className="sr-only">Demo risk signals</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {SIGNAL_TOGGLES.map((toggle) => (
              <label
                key={toggle.key}
                className={cn(
                  'flex cursor-pointer items-center justify-between gap-3 rounded-md border px-3 py-2 text-sm font-medium',
                  signals[toggle.key] ? 'border-primary bg-accent' : 'border-input',
                )}
              >
                <span>{toggle.label}</span>
                <input
                  type="checkbox"
                  checked={signals[toggle.key]}
                  onChange={(event) =>
                    setSignals((prev) => ({ ...prev, [toggle.key]: event.target.checked }))
                  }
                  aria-label={`${toggle.label}: ${signals[toggle.key] ? 'on' : 'off'}`}
                  className="h-4 w-4 accent-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring"
                />
              </label>
            ))}
          </div>
        </fieldset>
        <div className="mt-4 max-w-xs">
          <label htmlFor="historicalAverage" className="mb-1 block text-sm font-medium">
            Historical average payment ($)
          </label>
          <input
            id="historicalAverage"
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            value={historicalAverage}
            onChange={(event) => setHistoricalAverage(event.target.value)}
            placeholder="87"
            className={inputClass}
          />
          <p className="mt-1 text-xs text-muted-foreground">
            Demo user-history data for the risk engine. Leave blank if unknown.
          </p>
        </div>
      </section>

      {error !== null && (
        <p className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm" role="alert" aria-live="polite">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring sm:w-auto"
      >
        {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
        {submitting ? 'Analyzing payment...' : 'Review payment'}
      </button>
      {submitting && (
        <span className="sr-only" role="status" aria-live="polite">
          Analyzing payment...
        </span>
      )}
    </form>
  );
}
