import { FlaskConical } from 'lucide-react';

/** Shown only when VITE_CHECKOUT_MOCK=true. Impossible to miss on purpose. */
export default function MockBanner() {
  return (
    <div
      className="mb-4 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900"
      role="status"
    >
      <p className="flex items-center gap-2 font-semibold">
        <FlaskConical className="h-4 w-4" aria-hidden="true" />
        Development preview — mock responses
      </p>
      <p className="mt-1 text-xs">
        This build uses simulated risk results. It does not call GuardPay or PayPal, creates no
        order, and moves no money. Disable VITE_CHECKOUT_MOCK for the real Sandbox flow.
      </p>
    </div>
  );
}
