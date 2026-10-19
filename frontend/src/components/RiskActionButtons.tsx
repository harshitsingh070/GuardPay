import { useState } from 'react';
import { Link } from 'react-router-dom';
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
      <h2 className="text-lg font-semibold">Recommended action: {recommended}</h2>
      <div className="mt-3 grid gap-2 sm:grid-cols-3">
        <button
          type="button"
          onClick={() => setChoice('stop')}
          aria-pressed={choice === 'stop'}
          className={cn(
            'rounded-md px-4 py-2.5 text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
            stopPrimary
              ? 'bg-red-600 text-white hover:bg-red-700'
              : 'border border-input bg-background hover:bg-accent',
          )}
        >
          🛑 Stop payment
        </button>
        <button
          type="button"
          onClick={() => setChoice('verify')}
          aria-pressed={choice === 'verify'}
          className={cn(
            'rounded-md px-4 py-2.5 text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
            verifyPrimary && !stopPrimary
              ? 'bg-amber-500 text-white hover:bg-amber-600'
              : 'border border-input bg-background hover:bg-accent',
          )}
        >
          Verify
        </button>
        <button
          type="button"
          onClick={() => setChoice('continue')}
          aria-pressed={choice === 'continue'}
          className="rounded-md border border-input bg-background px-4 py-2.5 text-sm font-semibold hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          Continue anyway
        </button>
      </div>
      {choice !== null && (
        <div className="mt-3 rounded-md border p-3 text-sm" role="status" aria-live="polite">
          <p>{NOTICES[choice]}</p>
          <Link to="/payment" className="mt-2 inline-block font-medium underline">
            Evaluate another payment
          </Link>
        </div>
      )}
    </div>
  );
}
