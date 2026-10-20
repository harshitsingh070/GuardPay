import { ExternalLink, Loader2, ShieldCheck } from 'lucide-react';
import PayPalLogo from '@/components/checkout/PayPalLogo';
import { primaryButtonClass, secondaryButtonClass } from '@/components/checkout/styles';
import { openExternalUrl } from '@/services/native-bridge';
import type { GuardedPaypalOrder } from '@/types/payment';

interface Props {
  order: GuardedPaypalOrder;
  capturing: boolean;
  error: string | null;
  onCapture: () => void;
  onCancel: () => void;
}

/**
 * Approval happens on PayPal's Sandbox checkout page. Capture is always
 * initiated server-side and re-checks the stored risk decision.
 */
export default function PayPalApprovalPanel({
  order,
  capturing,
  error,
  onCapture,
  onCancel,
}: Props) {
  return (
    <section
      className="rounded-2xl border border-[#e1e6eb] bg-white p-5 shadow-sm sm:p-6"
      aria-labelledby="paypal-approval-heading"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="paypal-approval-heading" className="text-lg font-bold text-[#001435]">
          Continue to PayPal Sandbox
        </h2>
        <PayPalLogo className="text-lg" markClassName="h-6 w-auto" />
      </div>
      <p className="mt-1 text-sm text-[#6c7378]">
        Approve this order with your PayPal Sandbox test account, then return here to finish.
        GuardPay captures the payment on the server and confirms the final status with PayPal.
      </p>

      <dl className="mt-4 rounded-xl border border-[#e1e6eb] bg-[#f9fafb] p-3 text-sm">
        <div className="flex items-center justify-between gap-3">
          <dt className="text-[#6c7378]">PayPal order</dt>
          <dd className="break-all font-mono text-xs text-[#001435]">{order.orderId}</dd>
        </div>
      </dl>

      {error !== null && (
        <p
          className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          role="alert"
        >
          {error}
        </p>
      )}

      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <a
          href={order.approvalUrl}
          target="_blank"
          rel="noreferrer"
          onClick={(event) => {
            if (openExternalUrl(order.approvalUrl)) event.preventDefault();
          }}
          className={secondaryButtonClass}
        >
          <ExternalLink className="h-4 w-4" aria-hidden="true" />
          Approve in PayPal Sandbox
        </a>
        <button
          type="button"
          onClick={onCapture}
          disabled={capturing}
          className={primaryButtonClass}
        >
          {capturing && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
          {capturing ? 'Confirming with PayPal…' : 'I approved — capture payment'}
        </button>
      </div>

      <div className="mt-4 flex flex-col-reverse items-start gap-3 border-t border-[#e1e6eb] pt-4 sm:flex-row sm:items-center sm:justify-between">
        <button
          type="button"
          onClick={onCancel}
          disabled={capturing}
          className="text-sm font-semibold text-[#0070e0] underline-offset-4 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0070e0] disabled:opacity-60"
        >
          Cancel payment
        </button>
        <p className="flex items-center gap-1.5 text-xs text-[#6c7378]">
          <ShieldCheck className="h-3.5 w-3.5 text-[#0070e0]" aria-hidden="true" />
          Final status is confirmed by PayPal, not the browser.
        </p>
      </div>
    </section>
  );
}
