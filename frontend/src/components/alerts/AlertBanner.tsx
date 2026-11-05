import { useEffect } from 'react';
import { AlertTriangle, BellRing, ShieldAlert, X } from 'lucide-react';
import { cn } from '@/utils/cn';
import { classifyFraudPattern } from '@/utils/fraud-pattern';
import type { Alert } from '@/types/alerts';

const severityStyles: Record<Alert['severity'], { container: string; Icon: typeof AlertTriangle }> = {
  WARNING: {
    container: 'border-amber-200 bg-amber-50 text-amber-950',
    Icon: AlertTriangle,
  },
  HIGH: {
    container: 'border-orange-200 bg-orange-50 text-orange-950',
    Icon: BellRing,
  },
  CRITICAL: {
    container: 'border-red-200 bg-red-50 text-red-950',
    Icon: ShieldAlert,
  },
};

export default function AlertBanner({
  alert,
  onDismiss,
}: {
  alert: Alert | null;
  onDismiss: () => void;
}) {
  useEffect(() => {
    if (alert === null) return;
    const timer = setTimeout(onDismiss, 10_000);
    return () => clearTimeout(timer);
  }, [alert, onDismiss]);

  if (alert === null) return null;
  const { container, Icon } = severityStyles[alert.severity];
  const fraudPattern = classifyFraudPattern({
    codes: alert.signals,
    action: alert.recommendedAction,
  });

  return (
    <div
      className="fixed bottom-4 right-4 z-50 w-[calc(100%-2rem)] max-w-sm"
      role="alert"
      aria-live="assertive"
    >
      <div className={cn('rounded-2xl border p-4 shadow-lg shadow-black/5', container)}>
        <div className="flex items-start gap-3">
          <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-bold">{alert.title}</p>
            <p className="mt-1 text-xs leading-relaxed">{alert.message}</p>
            {fraudPattern !== null && (
              <p className="mt-1.5 text-xs font-semibold">Likely fraud type: {fraudPattern.label}</p>
            )}
            {alert.amount !== null && (
              <p className="mt-2 text-xs font-semibold tabular-nums">
                {alert.currency ?? 'USD'} {alert.amount.toFixed(2)}
                {alert.recipientName === null ? '' : ` · ${alert.recipientName}`}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-lg p-1 transition-colors hover:bg-black/5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current"
            aria-label="Dismiss alert"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  );
}
