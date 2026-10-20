import { ChevronDown, FlaskConical } from 'lucide-react';

/**
 * Hackathon presets. They only fill payment context — never a risk verdict.
 * The backend still has to evaluate the payment.
 */
export default function DemoPresets({
  onSelect,
  onReset,
}: {
  onSelect: (kind: 'safe' | 'suspicious') => void;
  onReset: () => void;
}) {
  const buttonClass =
    'rounded-full border border-[#cbd2d6] bg-white px-4 py-2 text-xs font-semibold text-[#001435] transition-colors hover:bg-[#f5f7fa] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0070e0]';

  return (
    <details className="group rounded-2xl border border-[#e1e6eb] bg-white p-4 text-sm shadow-sm">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-2 font-semibold text-[#001435] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0070e0] [&::-webkit-details-marker]:hidden">
        <span className="flex items-center gap-2">
          <FlaskConical className="h-4 w-4 text-[#6c7378]" aria-hidden="true" />
          Demo scenarios
        </span>
        <ChevronDown
          className="h-4 w-4 text-[#6c7378] transition-transform group-open:rotate-180"
          aria-hidden="true"
        />
      </summary>
      <p className="mt-2 text-xs text-[#6c7378]">
        Fill the form with sample payment context. GuardPay still runs the real check.
      </p>
      <div className="mt-2 flex flex-wrap gap-2">
        <button type="button" onClick={() => onSelect('safe')} className={buttonClass}>
          Safe payment
        </button>
        <button type="button" onClick={() => onSelect('suspicious')} className={buttonClass}>
          Suspicious payment
        </button>
        <button type="button" onClick={onReset} className={buttonClass}>
          Reset
        </button>
      </div>
    </details>
  );
}
