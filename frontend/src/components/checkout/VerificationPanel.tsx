import { useState } from 'react';
import { KeyRound, Loader2, ShieldCheck } from 'lucide-react';
import { inputClass, primaryButtonClass } from '@/components/checkout/styles';
import type { GuardedVerification } from '@/types/payment';

/** The subset the panel needs; satisfied by both the challenge and a confirm result. */
type VerificationView = Pick<
  GuardedVerification,
  'attemptsRemaining' | 'simulated' | 'channelHint' | 'challengeCode'
>;

interface Props {
  verification: VerificationView;
  submitting: boolean;
  error: string | null;
  onSubmit: (code: string) => void;
  onCancel: () => void;
}

/**
 * Controlled VERIFY challenge. The outcome is recorded by the backend; the
 * browser only supplies the answer. Until verification succeeds, no PayPal
 * order exists and there is nothing to approve or capture.
 */
export default function VerificationPanel({
  verification,
  submitting,
  error,
  onSubmit,
  onCancel,
}: Props) {
  const [code, setCode] = useState('');

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (code.trim() === '' || submitting) return;
    onSubmit(code.trim());
  }

  return (
    <section className="rounded-2xl border border-[#e1e6eb] bg-white p-5 shadow-sm" aria-labelledby="verification-heading">
      <div className="flex items-center gap-2">
        <KeyRound className="h-5 w-5 text-[#0070e0]" aria-hidden="true" />
        <h2 id="verification-heading" className="text-lg font-semibold">
          Confirm this payment
        </h2>
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        GuardPay needs one more confirmation before this payment can continue. No PayPal order has
        been created and no money can move until this check succeeds.
      </p>

      {verification.simulated && (
        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <p className="font-medium">Simulated verification channel</p>
          <p className="mt-0.5 text-xs">
            {verification.channelHint} This is a prototype stand-in, not real identity verification.
          </p>
          {verification.challengeCode !== undefined && (
            <p className="mt-2 text-xs">
              Development code:{' '}
              <span className="font-mono text-base font-semibold tracking-widest">
                {verification.challengeCode}
              </span>
            </p>
          )}
        </div>
      )}

      <form onSubmit={handleSubmit} className="mt-4 space-y-3">
        <label htmlFor="verification-code" className="block text-sm font-medium">
          Verification code
        </label>
        <input
          id="verification-code"
          name="verification-code"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          disabled={submitting}
          className={inputClass}
          placeholder="Enter the code"
        />

        {error !== null && (
          <p
            className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
            role="alert"
          >
            {error}
          </p>
        )}

        <p className="text-xs text-muted-foreground">
          {verification.attemptsRemaining} attempt
          {verification.attemptsRemaining === 1 ? '' : 's'} remaining.
        </p>

        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <button type="submit" disabled={submitting} className={primaryButtonClass}>
            {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {submitting ? 'Verifying…' : 'Confirm verification'}
          </button>
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="text-sm font-medium text-muted-foreground underline disabled:opacity-60"
          >
            Cancel payment
          </button>
        </div>
      </form>

      <p className="mt-4 flex items-center gap-1.5 border-t pt-4 text-xs text-muted-foreground">
        <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
        The verification result is decided on the server, never in the browser.
      </p>
    </section>
  );
}
