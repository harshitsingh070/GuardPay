import type { CheckoutPhase } from '@/types/checkout';
import type { GuardedPaymentResponse } from '@/types/payment';
import type { PayPalCaptureResponse } from '@/types/paypal';

/** Collapsible demo diagnostics. Backend-sourced values only — no raw JSON. */
export default function DeveloperDetails({
  phase,
  result,
  capture,
}: {
  phase: CheckoutPhase;
  result: GuardedPaymentResponse | null;
  capture: PayPalCaptureResponse | null;
}) {
  return (
    <details className="rounded-xl border p-4 text-sm">
      <summary className="cursor-pointer font-medium text-muted-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring">
        Developer details
      </summary>
      <dl className="mt-3 space-y-1.5 text-muted-foreground">
        <div className="flex flex-wrap gap-2">
          <dt>Checkout phase:</dt>
          <dd className="font-mono text-xs">{phase}</dd>
        </div>
        <div className="flex flex-wrap gap-2">
          <dt>Payment ID:</dt>
          <dd className="break-all font-mono text-xs">{result?.paymentId ?? '—'}</dd>
        </div>
        <div className="flex flex-wrap gap-2">
          <dt>AI status:</dt>
          <dd>{result?.risk.aiStatus ?? '—'}</dd>
        </div>
        <div className="flex flex-wrap gap-2">
          <dt>Signals:</dt>
          <dd className="break-words">
            {result?.risk.automaticSignals.map((signal) => signal.type).join(', ') || '—'}
          </dd>
        </div>
        <div className="flex flex-wrap gap-2">
          <dt>Triggered weights:</dt>
          <dd className="break-words">
            {result?.risk.findings
              .filter((finding) => finding.triggered)
              .map((finding) => `${finding.code} +${finding.points}`)
              .join(', ') || 'none'}
          </dd>
        </div>
        <div className="flex flex-wrap gap-2">
          <dt>PayPal order:</dt>
          <dd className="break-all font-mono text-xs">
            {result?.paypalOrder?.orderId ?? 'none'}
          </dd>
        </div>
        <div className="flex flex-wrap gap-2">
          <dt>Capture ID:</dt>
          <dd className="break-all font-mono text-xs">{capture?.captureId ?? '—'}</dd>
        </div>
      </dl>
    </details>
  );
}
