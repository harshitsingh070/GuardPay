import { Loader2, ShieldCheck } from 'lucide-react';
import type { CheckoutDraft } from '@/types/checkout';

const STEPS = [
  'Comparing this payment with your GuardPay history',
  'Reviewing the payment message and link for scam signals',
  'Applying the deterministic risk rules',
];

/** Transient CHECKING_RISK view. It never claims to inspect PayPal internals. */
export default function SecurityCheckPanel({ draft }: { draft: CheckoutDraft }) {
  return (
    <div
      className="rounded-2xl border border-[#e1e6eb] bg-white p-6 shadow-sm"
      role="status"
      aria-live="polite"
      aria-label="Checking payment safety"
    >
      <div className="flex items-center gap-3">
        <span className="grid h-11 w-11 place-items-center rounded-full bg-[#eaf2ff] text-[#0070e0]">
          <Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />
        </span>
        <div>
          <h2 className="text-lg font-semibold">Checking this payment</h2>
          <p className="text-sm text-muted-foreground">
            {draft.amount.trim() === ''
              ? 'GuardPay is evaluating the payment.'
              : `GuardPay is evaluating $${Number(draft.amount).toFixed(2)} before PayPal checkout.`}
          </p>
        </div>
      </div>

      <ul className="mt-5 space-y-3">
        {STEPS.map((step, index) => (
          <li key={step} className="flex items-center gap-3 text-sm">
            <span
              className="grid h-7 w-7 shrink-0 place-items-center rounded-full border border-[#e1e6eb] bg-[#f5f7fa] text-xs font-semibold text-[#6c7378]"
              aria-hidden="true"
            >
              {index + 1}
            </span>
            <span className="flex-1">{step}</span>
            <span className="h-2 w-16 animate-pulse rounded-full bg-[#e1e6eb]" aria-hidden="true" />
          </li>
        ))}
      </ul>

      <p className="mt-5 flex items-start gap-2 rounded-xl bg-[#f5f7fa] p-3 text-xs text-[#6c7378]">
        <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        This can take a few seconds. The Pay button stays disabled and no PayPal order exists
        while the check runs.
      </p>
    </div>
  );
}
