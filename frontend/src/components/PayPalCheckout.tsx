import { useState } from 'react';
import { ExternalLink, Loader2 } from 'lucide-react';
import {
  ApiRequestError,
  PayPalGateError,
  capturePayPalOrder,
  createPayPalOrder,
} from '@/services/api-client';
import type { RiskEvaluationRequest } from '@/types/risk';
import type { PayPalCaptureResponse, PayPalOrderResponse } from '@/types/paypal';

type Phase =
  | { name: 'idle' }
  | { name: 'creating' }
  | { name: 'approval'; order: PayPalOrderResponse }
  | { name: 'capturing'; order: PayPalOrderResponse }
  | { name: 'captured'; capture: PayPalCaptureResponse };

const buttonClass =
  'inline-flex w-full items-center justify-center gap-2 rounded-md px-4 py-2.5 text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto';

/**
 * Sandbox checkout for ALLOW decisions only: create order → approve on
 * paypal.com → capture. Every permission comes from the backend — the
 * approval URL and capture result are server-issued, never computed here.
 */
export default function PayPalCheckout({ request }: { request: RiskEvaluationRequest }) {
  const [phase, setPhase] = useState<Phase>({ name: 'idle' });
  const [error, setError] = useState<string | null>(null);

  function failureMessage(err: unknown, fallback: string): string {
    if (err instanceof PayPalGateError) return err.gate.message;
    if (err instanceof ApiRequestError) return err.message;
    return fallback;
  }

  async function handleCreate() {
    setError(null);
    setPhase({ name: 'creating' });
    try {
      const order = await createPayPalOrder({
        ...request,
        currency: 'USD',
        description: 'GuardPay protected payment',
      });
      if (!order.allowed || order.approvalUrl === null) {
        setError(order.message);
        setPhase({ name: 'idle' });
        return;
      }
      setPhase({ name: 'approval', order });
    } catch (err) {
      setError(failureMessage(err, 'Unable to create the PayPal order. Please try again.'));
      setPhase({ name: 'idle' });
    }
  }

  async function handleCapture(orderId: string, order: PayPalOrderResponse) {
    setError(null);
    setPhase({ name: 'capturing', order });
    try {
      const capture = await capturePayPalOrder(orderId);
      setPhase({ name: 'captured', capture });
    } catch (err) {
      setError(failureMessage(err, 'Unable to capture the PayPal order. Please try again.'));
      setPhase({ name: 'approval', order });
    }
  }

  if (phase.name === 'captured') {
    return (
      <div className="rounded-lg border border-green-200 bg-green-50 p-4 sm:p-6 dark:border-green-900 dark:bg-green-950" role="status" aria-live="polite">
        <h2 className="text-lg font-semibold">Payment captured</h2>
        <dl className="mt-2 space-y-1 text-sm">
          <div className="flex gap-2">
            <dt className="text-muted-foreground">Status:</dt>
            <dd className="font-medium">{phase.capture.status}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-muted-foreground">Capture ID:</dt>
            <dd className="font-mono text-xs">{phase.capture.captureId}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-muted-foreground">Amount:</dt>
            <dd className="font-medium">
              {phase.capture.currency} {phase.capture.amount}
            </dd>
          </div>
        </dl>
        <p className="mt-2 text-xs text-muted-foreground">
          Sandbox capture — no real money moved. GuardPay allowed this payment (ALLOW).
        </p>
      </div>
    );
  }

  if (phase.name === 'approval' || phase.name === 'capturing') {
    const order = phase.order;
    const capturing = phase.name === 'capturing';
    return (
      <div className="rounded-lg border p-4 sm:p-6" aria-live="polite">
        <h2 className="text-lg font-semibold">PayPal Sandbox checkout</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Order <span className="font-mono text-xs">{order.orderId}</span> created ({order.currency}{' '}
          {order.amount}). Approve it with your Sandbox Personal test account, then return here
          to capture.
        </p>
        {error !== null && (
          <p className="mt-3 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm" role="alert">
            {error}
          </p>
        )}
        <div className="mt-4 flex flex-col gap-2 sm:flex-row">
          <a
            href={order.approvalUrl ?? '#'}
            target="_blank"
            rel="noreferrer"
            className={`${buttonClass} border border-input bg-background hover:bg-accent`}
          >
            <ExternalLink className="h-4 w-4" aria-hidden="true" />
            Approve in PayPal Sandbox
          </a>
          <button
            type="button"
            disabled={capturing}
            onClick={() => handleCapture(order.orderId ?? '', order)}
            className={`${buttonClass} bg-primary text-primary-foreground hover:bg-primary/90`}
          >
            {capturing && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {capturing ? 'Capturing...' : 'Capture payment'}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border p-4 sm:p-6">
      <h2 className="text-lg font-semibold">Pay with PayPal Sandbox</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        GuardPay rated this payment ALLOW. Create a real Sandbox order, approve it as the
        buyer, then capture it here. The server re-checks the risk decision before capture.
      </p>
      {error !== null && (
        <p className="mt-3 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm" role="alert" aria-live="polite">
          {error}
        </p>
      )}
      <button
        type="button"
        disabled={phase.name === 'creating'}
        onClick={handleCreate}
        className={`${buttonClass} mt-4 bg-primary text-primary-foreground hover:bg-primary/90`}
      >
        {phase.name === 'creating' && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
        {phase.name === 'creating' ? 'Creating order...' : 'Continue to PayPal'}
      </button>
    </div>
  );
}
