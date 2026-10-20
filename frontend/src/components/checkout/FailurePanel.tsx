import { AlertCircle } from 'lucide-react';
import { primaryButtonClass, secondaryButtonClass } from '@/components/checkout/styles';

export default function FailurePanel({
  message,
  retryLabel,
  onRetry,
  onStartOver,
}: {
  message: string;
  retryLabel: string | null;
  onRetry: (() => void) | null;
  onStartOver: () => void;
}) {
  return (
    <section
      className="rounded-2xl border border-red-200 bg-red-50/60 p-5 shadow-sm"
      role="alert"
      aria-live="assertive"
    >
      <div className="flex items-start gap-3">
        <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" aria-hidden="true" />
        <div>
          <h2 className="text-lg font-semibold">Payment not completed</h2>
          <p className="mt-1 text-sm text-red-800">{message}</p>
        </div>
      </div>
      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        {retryLabel !== null && onRetry !== null && (
          <button type="button" onClick={onRetry} className={primaryButtonClass}>
            {retryLabel}
          </button>
        )}
        <button type="button" onClick={onStartOver} className={secondaryButtonClass}>
          Start a new payment
        </button>
      </div>
    </section>
  );
}
