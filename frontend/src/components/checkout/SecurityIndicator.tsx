import { ShieldCheck } from 'lucide-react';
import { cn } from '@/utils/cn';

/** Small trust marker. Never implies GuardPay is PayPal or part of PayPal. */
export default function SecurityIndicator({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-800',
        className,
      )}
    >
      <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
      GuardPay protection
    </span>
  );
}
