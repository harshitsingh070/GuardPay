import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import RiskActionButtons from '@/components/RiskActionButtons';
import RiskFindingList from '@/components/RiskFindingList';
import RiskWarning from '@/components/RiskWarning';
import RiskPipeline, { type PipelineContext } from '@/components/RiskPipeline';
import { usePageTitle } from '@/hooks/usePageTitle';
import {
  ApiRequestError,
  PayPalGateError,
  capturePayPalOrder,
  createGuardedPayment,
} from '@/services/api-client';
import type {
  GuardedPaymentResponse,
  GuardedPaypalOrder,
} from '@/types/payment';
import type { PayPalCaptureResponse } from '@/types/paypal';
import { riskPanelClass } from '@/utils/risk-styles';

const inputClass =
  'w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring';

type CaptureState =
  | { name: 'idle' }
  | { name: 'capturing' }
  | { name: 'captured'; capture: PayPalCaptureResponse };

/**
 * Primary GuardPay flow: business context in, server decision out.
 * No risk toggles anywhere — amount, names, message, and URL only.
 */
export default function GuardedPaymentPage() {
  usePageTitle('Pay securely');
  const [amount, setAmount] = useState('');
  const [recipient, setRecipient] = useState('');
  const [merchant, setMerchant] = useState('');
  const [message, setMessage] = useState('');
  const [url, setUrl] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GuardedPaymentResponse | null>(null);
  const [submitted, setSubmitted] = useState<PipelineContext>({
    amount: '',
    currency: 'USD',
    recipient: '',
    merchant: '',
  });
  const [capture, setCapture] = useState<CaptureState>({ name: 'idle' });
  const [captureError, setCaptureError] = useState<string | null>(null);

  const HERO_MESSAGE =
    'You need to pay this invoice immediately to avoid account suspension. Do not contact anyone else. This is the official support department. Use the payment link below.';
  const HERO_URL = 'https://example-suspicious-domain.test/pay';

  /** Demo input presets only — they fill payment context, never risk verdicts. */
  function applyPreset(kind: 'safe' | 'suspicious') {
    if (kind === 'safe') {
      setAmount('45');
      setRecipient('Known Co');
      setMerchant('Known Shop');
      setMessage('Monthly software subscription invoice for $45.');
      setUrl('');
    } else {
      setAmount('2499');
      setRecipient('Fresh Stranger');
      setMerchant('Unknown Shop');
      setMessage(HERO_MESSAGE);
      setUrl(HERO_URL);
    }
    setResult(null);
    setError(null);
  }

  /** Clears frontend demo state only — no database or PayPal records touched. */
  function handleReset() {
    setAmount('');
    setRecipient('');
    setMerchant('');
    setMessage('');
    setUrl('');
    setResult(null);
    setError(null);
    setCapture({ name: 'idle' });
    setCaptureError(null);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const parsed = Number(amount);
    if (amount.trim() === '' || Number.isNaN(parsed) || parsed <= 0) {
      setError('Please enter a payment amount greater than zero.');
      return;
    }
    setSubmitting(true);
    setError(null);
    setResult(null);
    setCapture({ name: 'idle' });
    setCaptureError(null);
    try {
      const response = await createGuardedPayment({
        amount: parsed,
        currency: 'USD',
        description: 'GuardPay protected payment',
        recipientName: recipient.trim() === '' ? undefined : recipient.trim(),
        merchantName: merchant.trim() === '' ? undefined : merchant.trim(),
        contentText: message.trim() === '' ? undefined : message.trim(),
        contentUrl: url.trim() === '' ? undefined : url.trim(),
      });
      setSubmitted({ amount, currency: 'USD', recipient, merchant });
      setResult(response);
    } catch (err) {
      setError(
        err instanceof ApiRequestError
          ? err.message
          : 'Unable to start this payment. Please try again.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCapture(order: GuardedPaypalOrder) {
    setCapture({ name: 'capturing' });
    setCaptureError(null);
    try {
      const done = await capturePayPalOrder(order.orderId);
      setCapture({ name: 'captured', capture: done });
    } catch (err) {
      setCaptureError(
        err instanceof PayPalGateError || err instanceof ApiRequestError
          ? err.message
          : 'Unable to capture this payment. Please try again.',
      );
      setCapture({ name: 'idle' });
    }
  }

  return (
    <section className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold">Pay securely</h1>
      <p className="mt-2 text-muted-foreground">
        Enter the payment. GuardPay checks history, device context, and content
        automatically — no payment moves until it passes.
      </p>

      <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Demo scenarios">
        <button
          type="button"
          onClick={() => applyPreset('safe')}
          className="rounded-md border border-input bg-background px-4 py-2 text-sm font-semibold hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          Safe Payment
        </button>
        <button
          type="button"
          onClick={() => applyPreset('suspicious')}
          className="rounded-md border border-input bg-background px-4 py-2 text-sm font-semibold hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          Suspicious Payment
        </button>
        <button
          type="button"
          onClick={handleReset}
          className="rounded-md border border-input bg-background px-4 py-2 text-sm font-semibold hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          Reset Demo
        </button>
      </div>

      <form onSubmit={handleSubmit} aria-busy={submitting} className="mt-6 space-y-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <label htmlFor="gp-amount" className="mb-1 block text-sm font-medium">
              Amount ($)
            </label>
            <input
              id="gp-amount"
              type="number"
              min="0.01"
              step="0.01"
              inputMode="decimal"
              required
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="45.00"
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="gp-recipient" className="mb-1 block text-sm font-medium">
              Recipient
            </label>
            <input
              id="gp-recipient"
              type="text"
              value={recipient}
              onChange={(event) => setRecipient(event.target.value)}
              placeholder="Who is this for?"
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="gp-merchant" className="mb-1 block text-sm font-medium">
              Merchant
            </label>
            <input
              id="gp-merchant"
              type="text"
              value={merchant}
              onChange={(event) => setMerchant(event.target.value)}
              placeholder="Merchant name"
              className={inputClass}
            />
          </div>
        </div>
        <div>
          <label htmlFor="gp-message" className="mb-1 block text-sm font-medium">
            Payment message <span className="font-normal text-muted-foreground">(optional)</span>
          </label>
          <textarea
            id="gp-message"
            rows={3}
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            placeholder="Monthly software subscription invoice for $45."
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="gp-url" className="mb-1 block text-sm font-medium">
            Payment URL <span className="font-normal text-muted-foreground">(optional)</span>
          </label>
          <input
            id="gp-url"
            type="url"
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder="https://…"
            className={inputClass}
          />
        </div>

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
          {submitting ? 'Checking safety...' : 'Pay securely'}
        </button>
      </form>

      {result !== null && (
        <div className="mt-8 space-y-6" aria-live="polite">
          <RiskWarning level={result.risk.riskLevel} />
          <RiskPipeline result={result} context={submitted} />
          <div className={riskPanelClass(result.risk.riskLevel)}>
            <h2 className="text-lg font-semibold">Why GuardPay flagged this</h2>
            <p className="mt-1 text-sm">{result.risk.explanation}</p>
          </div>
          {result.risk.aiStatus === 'UNAVAILABLE' && (
            <p className="rounded-md border p-3 text-sm" role="status">
              AI analysis unavailable. GuardPay continued using deterministic signals.
            </p>
          )}
          <RiskFindingList findings={result.risk.findings} />

          {result.paymentStatus === 'PAYPAL_AUTHORIZATION_REQUIRED' &&
          result.paypalOrder !== null ? (
            <PayPalContinuation
              order={result.paypalOrder}
              capture={capture}
              captureError={captureError}
              onCapture={() => handleCapture(result.paypalOrder as GuardedPaypalOrder)}
            />
          ) : (
            <RiskActionButtons recommended={result.risk.recommendedAction} />
          )}

          <details className="rounded-md border p-3 text-sm">
            <summary className="cursor-pointer font-medium text-muted-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring">
              Developer details
            </summary>
            <dl className="mt-2 space-y-1 text-muted-foreground">
              <div className="flex gap-2">
                <dt>Payment ID:</dt>
                <dd className="font-mono text-xs">{result.paymentId}</dd>
              </div>
              <div className="flex gap-2">
                <dt>AI status:</dt>
                <dd>{result.risk.aiStatus}</dd>
              </div>
              <div className="flex gap-2">
                <dt>Signals:</dt>
                <dd>{result.risk.automaticSignals.map((signal) => signal.type).join(', ')}</dd>
              </div>
              <div className="flex gap-2">
                <dt>Triggered weights:</dt>
                <dd>
                  {result.risk.findings
                    .filter((finding) => finding.triggered)
                    .map((finding) => `${finding.code} +${finding.points}`)
                    .join(', ') || 'none'}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt>PayPal order:</dt>
                <dd className="font-mono text-xs">{result.paypalOrder?.orderId ?? 'none'}</dd>
              </div>
              <div className="flex gap-2">
                <dt>Payment status:</dt>
                <dd>{result.paymentStatus}</dd>
              </div>
            </dl>
          </details>
        </div>
      )}

      <footer className="mt-10 border-t pt-4 text-xs text-muted-foreground">
        <p>
          GuardPay is a reference integration layer demonstrating how AI-powered
          payment safety can integrate with PayPal payment authorization flows.
          It is a hackathon prototype — not production fraud detection, not
          part of the official PayPal app, and not endorsed by PayPal.
        </p>
      </footer>
    </section>
  );
}

function PayPalContinuation({
  order,
  capture,
  captureError,
  onCapture,
}: {
  order: GuardedPaypalOrder;
  capture: CaptureState;
  captureError: string | null;
  onCapture: () => void;
}) {
  if (capture.name === 'captured') {
    return (
      <div className="rounded-lg border border-green-200 bg-green-50 p-4 dark:border-green-900 dark:bg-green-950" role="status">
        <h2 className="text-lg font-semibold">Payment captured</h2>
        <p className="mt-1 text-sm">
          Status: {capture.capture.status} · Capture ID:{' '}
          <span className="font-mono text-xs">{capture.capture.captureId}</span>
        </p>
        <p className="mt-2 text-xs text-muted-foreground">
          Sandbox capture — no real money moved. Final state is confirmed by PayPal webhooks.
        </p>
      </div>
    );
  }
  return (
    <div className="rounded-lg border p-4 sm:p-6">
      <h2 className="text-lg font-semibold">Continue to PayPal</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        GuardPay rated this payment ALLOW. Approve order{' '}
        <span className="font-mono text-xs">{order.orderId}</span> in the Sandbox, then
        return here to capture. Capture runs server-side after a fresh risk check.
      </p>
      {captureError !== null && (
        <p className="mt-3 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm" role="alert">
          {captureError}
        </p>
      )}
      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <a
          href={order.approvalUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center justify-center gap-2 rounded-md border border-input bg-background px-4 py-2.5 text-sm font-semibold hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          Approve in PayPal Sandbox
        </a>
        <button
          type="button"
          disabled={capture.name === 'capturing'}
          onClick={onCapture}
          className="inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          {capture.name === 'capturing' && (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          )}
          {capture.name === 'capturing' ? 'Capturing...' : 'Capture payment'}
        </button>
      </div>
    </div>
  );
}
