import { CheckCircle2, ShieldCheck } from 'lucide-react';
import type { CheckoutDraft } from '@/types/checkout';
import type { GuardedPaymentResponse } from '@/types/payment';
import type { PayPalCaptureResponse } from '@/types/paypal';

function money(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
}

function formatDate(value: string | null): string {
  if (value === null) return 'Just now';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Just now' : date.toLocaleString();
}

export default function ReceiptPanel({
  result,
  capture,
  completedAt,
  draft,
}: {
  result: GuardedPaymentResponse;
  capture: PayPalCaptureResponse;
  completedAt: string | null;
  draft: CheckoutDraft;
}) {
  const recipient = draft.recipient.trim() || 'New recipient';

  return (
    <section
      className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-6 shadow-sm"
      aria-labelledby="receipt-heading"
      aria-live="polite"
    >
      <div className="flex items-center gap-3">
        <CheckCircle2 className="h-8 w-8 shrink-0 text-emerald-600" aria-hidden="true" />
        <div>
          <h2 id="receipt-heading" className="text-xl font-bold">
            Payment complete
          </h2>
          <p className="text-sm text-muted-foreground">
            PayPal confirmed the capture. Sandbox payment — no real money moved.
          </p>
        </div>
      </div>

      <p className="mt-5 text-3xl font-bold tabular-nums">
        {money(capture.amount, capture.currency)}
      </p>
      <p className="text-sm text-muted-foreground">Paid to {recipient}</p>

      <dl className="mt-5 space-y-2 border-t border-emerald-200 pt-4 text-sm">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-muted-foreground">PayPal order ID</dt>
          <dd className="break-all font-mono text-xs">{capture.orderId}</dd>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-muted-foreground">PayPal capture ID</dt>
          <dd className="break-all font-mono text-xs">{capture.captureId}</dd>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-muted-foreground">Payment status</dt>
          <dd className="font-semibold">{capture.paymentStatus}</dd>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-muted-foreground">Date</dt>
          <dd>{formatDate(completedAt)}</dd>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-muted-foreground">GuardPay decision</dt>
          <dd className="flex items-center gap-1.5 font-semibold">
            <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
            {result.risk.recommendedAction} · {result.risk.riskLevel} ({result.risk.score}/100)
          </dd>
        </div>
      </dl>

      <p className="mt-4 text-xs text-muted-foreground">{result.risk.explanation}</p>
    </section>
  );
}
