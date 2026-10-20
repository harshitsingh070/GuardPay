import { Lock } from 'lucide-react';
import { PayPalMark } from '@/components/checkout/PayPalLogo';
import { cn } from '@/utils/cn';
import type { CheckoutDraft } from '@/types/checkout';

function money(value: string, currency = 'USD'): string {
  const parsed = Number(value);
  if (value.trim() === '' || Number.isNaN(parsed)) return `${currency} 0.00`;
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(parsed);
}

/** Sticky order summary. Values come from the submitted draft only. */
export default function OrderSummary({
  draft,
  paid,
  className,
}: {
  draft: CheckoutDraft;
  paid: boolean;
  className?: string;
}) {
  const recipient = draft.recipient.trim() || 'New recipient';
  const merchant = draft.merchant.trim();
  const description = draft.description.trim();

  return (
    <aside
      className={cn(
        'rounded-2xl border border-[#e1e6eb] bg-white p-5 shadow-sm lg:sticky lg:top-20',
        className,
      )}
      aria-label="Order summary"
    >
      <div className="flex items-center justify-between gap-2 border-b border-[#e1e6eb] pb-3">
        <h2 className="text-base font-bold text-[#001435]">Order summary</h2>
        <PayPalMark className="h-5 w-auto" />
      </div>

      <dl className="mt-4 space-y-3 text-sm">
        <div className="flex items-start justify-between gap-3">
          <dt className="text-[#6c7378]">Paying</dt>
          <dd className="max-w-[60%] break-words text-right font-semibold text-[#001435]">
            {recipient}
          </dd>
        </div>
        {merchant !== '' && (
          <div className="flex items-start justify-between gap-3">
            <dt className="text-[#6c7378]">Merchant</dt>
            <dd className="max-w-[60%] break-words text-right text-[#001435]">{merchant}</dd>
          </div>
        )}
        {description !== '' && (
          <div className="flex items-start justify-between gap-3">
            <dt className="text-[#6c7378]">For</dt>
            <dd className="max-w-[60%] break-words text-right text-[#6c7378]">{description}</dd>
          </div>
        )}
        <div className="flex items-start justify-between gap-3">
          <dt className="text-[#6c7378]">Payment method</dt>
          <dd className="flex items-center gap-1.5 text-right font-semibold text-[#001435]">
            <PayPalMark className="h-4 w-auto" />
            PayPal Sandbox
          </dd>
        </div>
      </dl>

      <div className="mt-4 flex items-baseline justify-between border-t border-dashed border-[#cbd2d6] pt-4">
        <span className="text-base font-bold text-[#001435]">{paid ? 'Paid' : 'Total'}</span>
        <span className="text-[1.75rem] font-bold tracking-tight text-[#001435] tabular-nums">
          {money(draft.amount)}
        </span>
      </div>

      <div className="mt-4 flex items-start gap-2 rounded-lg bg-[#f5f7fa] p-3 text-xs text-[#6c7378]">
        <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>
          Sandbox checkout. No real money moves. GuardPay checks the payment before a PayPal
          order is created.
        </span>
      </div>
    </aside>
  );
}
