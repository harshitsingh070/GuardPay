import { Check, ChevronDown, FileText, Lock, MessageSquareText, ShieldCheck } from 'lucide-react';
import type { CheckoutDraft } from '@/types/checkout';
import PayPalLogo, { PayPalMark } from '@/components/checkout/PayPalLogo';
import {
  inputClass,
  primaryButtonClass,
} from '@/components/checkout/styles';

interface Props {
  draft: CheckoutDraft;
  onChange: (patch: Partial<CheckoutDraft>) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  disabled: boolean;
  error: string | null;
}

export default function PaymentDetailsForm({
  draft,
  onChange,
  onSubmit,
  onCancel,
  disabled,
  error,
}: Props) {
  return (
    <form onSubmit={onSubmit} aria-busy={disabled} className="space-y-6" noValidate>
      <section aria-labelledby="payment-method-heading">
        <div className="flex items-center justify-between gap-3">
          <h2 id="payment-method-heading" className="text-xl font-bold text-[#001435]">
            Pay with
          </h2>
          <PayPalLogo className="text-base" markClassName="h-5 w-auto" />
        </div>

        <div className="mt-3 grid grid-cols-[auto_minmax(0,1fr)] items-center gap-x-3 gap-y-2 rounded-xl border-2 border-[#0070e0] bg-[#f5f9ff] p-3.5 sm:grid-cols-[auto_minmax(0,1fr)_auto]">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-white shadow-sm">
            <PayPalMark className="h-5 w-auto" />
          </span>
          <div className="min-w-0">
            <p className="font-semibold text-[#001435]">PayPal Sandbox</p>
            <p className="text-xs text-[#6c7378]">Test account. No real money moves.</p>
          </div>
          <span
            className="col-span-2 inline-flex w-fit items-center gap-1 rounded-full bg-[#0070e0] px-2.5 py-1 text-xs font-semibold text-white sm:col-span-1 sm:justify-self-end"
            aria-label="Selected payment method"
          >
            <Check className="h-3.5 w-3.5" aria-hidden="true" />
            Selected
          </span>
        </div>

        <p className="mt-2 text-xs text-[#6c7378]">
          GuardPay is not part of PayPal. This is a Sandbox integration demo.
        </p>
      </section>

      <section aria-labelledby="payment-details-heading">
        <h2 id="payment-details-heading" className="text-xl font-bold text-[#001435]">
          Payment details
        </h2>
        <p className="mt-1 text-sm text-[#6c7378]">
          Tell GuardPay who you are paying and why. This context is what the security check
          evaluates.
        </p>

        <div className="mt-4 space-y-4">
          <div>
            <label htmlFor="checkout-amount" className="mb-1 block text-sm font-medium text-[#001435]">
              Amount (USD)
            </label>
            <div className="relative">
              <span
                className="pointer-events-none absolute inset-y-0 left-0 grid w-9 place-items-center text-base text-[#6c7378] sm:text-sm"
                aria-hidden="true"
              >
                $
              </span>
              <input
                id="checkout-amount"
                type="number"
                min="0.01"
                step="0.01"
                inputMode="decimal"
                enterKeyHint="next"
                required
                value={draft.amount}
                onChange={(event) => onChange({ amount: event.target.value })}
                placeholder="45.00"
                className={`${inputClass} pl-9`}
              />
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="checkout-recipient" className="mb-1 block text-sm font-medium text-[#001435]">
                Recipient
              </label>
              <input
                id="checkout-recipient"
                type="text"
                value={draft.recipient}
                onChange={(event) => onChange({ recipient: event.target.value })}
                placeholder="Who is this for?"
                className={inputClass}
              />
            </div>
            <div>
              <label htmlFor="checkout-recipient-email" className="mb-1 block text-sm font-medium text-[#001435]">
                Recipient PayPal email
              </label>
              <input
                id="checkout-recipient-email"
                type="email"
                inputMode="email"
                required
                value={draft.recipientEmail}
                onChange={(event) => onChange({ recipientEmail: event.target.value })}
                placeholder="recipient@example.com"
                className={inputClass}
              />
            </div>
            <div className="sm:col-span-2">
              <label htmlFor="checkout-merchant" className="mb-1 block text-sm font-medium text-[#001435]">
                Merchant
              </label>
              <input
                id="checkout-merchant"
                type="text"
                value={draft.merchant}
                onChange={(event) => onChange({ merchant: event.target.value })}
                placeholder="Merchant name"
                className={inputClass}
              />
            </div>
          </div>

          <details className="group rounded-xl border border-[#e1e6eb] bg-[#f9fafb] p-3">
            <summary className="flex cursor-pointer list-none items-center gap-2 text-sm font-medium text-[#001435] [&::-webkit-details-marker]:hidden">
              <FileText className="h-4 w-4 shrink-0 text-[#6c7378]" aria-hidden="true" />
              Add invoice details (optional)
              <ChevronDown
                className="ml-auto h-4 w-4 text-[#6c7378] transition-transform group-open:rotate-180"
                aria-hidden="true"
              />
            </summary>
            <p className="mt-1.5 text-xs text-[#6c7378]">
              A message or payment link helps GuardPay spot scam patterns. It is shared only with
              the safety check.
            </p>

            <div className="mt-3 space-y-4">
              <div>
                <label htmlFor="checkout-description" className="mb-1 block text-sm font-medium text-[#001435]">
                  Payment description{' '}
                  <span className="font-normal text-[#6c7378]">(optional)</span>
                </label>
                <textarea
                  id="checkout-description"
                  rows={3}
                  value={draft.description}
                  onChange={(event) => onChange({ description: event.target.value })}
                  placeholder="Invoice, subscription, or message that came with this payment."
                  className={inputClass}
                />
                <p className="mt-1 flex items-center gap-1.5 text-xs text-[#6c7378]">
                  <MessageSquareText className="h-3.5 w-3.5" aria-hidden="true" />
                  Shared only with GuardPay&apos;s safety check, never posted publicly.
                </p>
              </div>

              <div>
                <label htmlFor="checkout-url" className="mb-1 block text-sm font-medium text-[#001435]">
                  Payment link{' '}
                  <span className="font-normal text-[#6c7378]">(optional)</span>
                </label>
                <input
                  id="checkout-url"
                  type="url"
                  inputMode="url"
                  value={draft.url}
                  onChange={(event) => onChange({ url: event.target.value })}
                  placeholder="https://…"
                  className={inputClass}
                />
              </div>
            </div>
          </details>
        </div>
      </section>

      <section
        aria-labelledby="security-explanation-heading"
        className="rounded-xl border border-[#cfe4ff] bg-[#f5f9ff] p-3 sm:p-4"
      >
        <h2
          id="security-explanation-heading"
          className="flex items-center gap-2 text-sm font-bold text-[#001435]"
        >
          <ShieldCheck className="h-4 w-4 text-[#0070e0]" aria-hidden="true" />
          What GuardPay checks
        </h2>
        <ul className="mt-2 grid gap-2 text-xs text-[#40505c] sm:grid-cols-3">
          <li className="flex items-start gap-1.5">
            <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#0070e0]" aria-hidden="true" />
            Payment history and unusual amounts.
          </li>
          <li className="flex items-start gap-1.5">
            <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#0070e0]" aria-hidden="true" />
            Message and link safety signals.
          </li>
          <li className="flex items-start gap-1.5">
            <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#0070e0]" aria-hidden="true" />
            A deterministic rules engine makes the final decision.
          </li>
        </ul>
      </section>

      {error !== null && (
        <p
          className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          role="alert"
        >
          {error}
        </p>
      )}

      <div className="flex flex-col-reverse gap-3 border-t border-[#e1e6eb] pt-5 sm:flex-row sm:items-center sm:justify-between">
        <button
          type="button"
          onClick={onCancel}
          disabled={disabled}
          className="inline-flex min-h-11 items-center justify-center text-sm font-semibold text-[#0070e0] underline-offset-4 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0070e0] disabled:cursor-not-allowed disabled:opacity-60"
        >
          Cancel and return
        </button>
        <div className="flex w-full flex-col gap-1.5 sm:w-auto sm:items-end">
          <button
            type="submit"
            disabled={disabled}
            className={`${primaryButtonClass} sm:min-w-52`}
          >
            <PayPalMark className="h-5 w-auto" />
            {draft.amount.trim() === ''
              ? 'Pay securely'
              : `Pay $${Number(draft.amount).toFixed(2)}`}
          </button>
          <p className="text-center text-[11px] text-[#6c7378] sm:text-right">
            You will approve the payment in PayPal Sandbox next.
          </p>
        </div>
      </div>
    </form>
  );
}
