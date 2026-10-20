import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, OctagonX, ShieldCheck } from 'lucide-react';
import type { RecommendedAction } from '@/types/risk';
import { cn } from '@/utils/cn';

type DemoChoice = 'stop' | 'verify' | 'continue' | null;

const NOTICES: Record<Exclude<DemoChoice, null>, string> = {
  stop: 'Payment stopped (demo). No money moved and nothing was sent anywhere.',
  verify: 'Verification flow coming soon (demo). For now, contact the recipient through a trusted channel.',
  continue: 'You chose to continue (demo). No payment was processed.',
};

/**
 * STOP / VERIFY / Continue actions. DEMO UI only — no payment is processed,
 * no API is called. The recommended action comes from the API response.
 */
export default function RiskActionButtons({ recommended }: { recommended: RecommendedAction }) {
  const [choice, setChoice] = useState<DemoChoice>(null);

  const stopPrimary = recommended === 'STOP';
  const verifyPrimary = recommended === 'VERIFY';

  return (
    <div>
      <h2 className="text-lg font-bold text-guardpay-ink">
        Recommended action: {recommended}
      </h2>
      <p className="mt-1 text-sm text-guardpay-muted">
        This is a demo. Choosing an action does not process a payment.
      </p>
      <div className="mt-4 grid gap-2 sm:grid-cols-3">
        <button
          type="button"
          onClick={() => setChoice('stop')}
          aria-pressed={choice === 'stop'}
          className={cn(
            'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue',
            stopPrimary
              ? 'bg-red-600 text-white shadow-sm hover:bg-red-700'
              : 'border border-guardpay-line bg-white text-guardpay-ink hover:bg-guardpay-canvas',
          )}
        >
          <OctagonX className="h-4 w-4" aria-hidden="true" />
          Stop payment
        </button>
        <button
          type="button"
          onClick={() => setChoice('verify')}
          aria-pressed={choice === 'verify'}
          className={cn(
            'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue',
            verifyPrimary && !stopPrimary
              ? 'bg-amber-500 text-white shadow-sm hover:bg-amber-600'
              : 'border border-guardpay-line bg-white text-guardpay-ink hover:bg-guardpay-canvas',
          )}
        >
          <ShieldCheck className="h-4 w-4" aria-hidden="true" />
          Verify
        </button>
        <button
          type="button"
          onClick={() => setChoice('continue')}
          aria-pressed={choice === 'continue'}
          className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-guardpay-line bg-white px-4 py-2.5 text-sm font-semibold text-guardpay-ink transition-colors hover:bg-guardpay-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue"
        >
          Continue anyway
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      {choice !== null && (
        <div
          className="mt-3 rounded-xl border border-guardpay-line bg-guardpay-canvas p-3.5 text-sm text-guardpay-ink"
          role="status"
          aria-live="polite"
        >
          <p>{NOTICES[choice]}</p>
          <Link
            to="/payment"
            className="mt-2 inline-flex items-center gap-1 font-semibold text-guardpay-blue hover:underline"
          >
            Evaluate another payment
            <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
          </Link>
        </div>
      )}
    </div>
  );
}
