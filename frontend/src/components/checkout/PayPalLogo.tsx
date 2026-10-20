import { cn } from '@/utils/cn';

export function PayPalMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 28 32"
      className={cn('shrink-0', className)}
      aria-hidden="true"
      focusable="false"
    >
      <path
        fill="#003087"
        fillRule="evenodd"
        d="M4 1h12.5c5.1 0 8.1 3 7.5 7.6-.7 4.7-4 7.4-9 7.4h-4.5l-1.5 9H3L4 1Zm8.4 5-1 6h3.1c2.2 0 3.4-1.1 3.6-2.9.2-1.7-.8-3.1-3-3.1h-2.7Z"
      />
      <path
        fill="#009cde"
        fillRule="evenodd"
        d="M9 8h12.5c5.1 0 8.1 3 7.5 7.6-.7 4.7-4 7.4-9 7.4h-4.5l-1.5 9H8L9 8Zm8.4 5-1 6h3.1c2.2 0 3.4-1.1 3.6-2.9.2-1.7-.8-3.1-3-3.1h-2.7Z"
      />
    </svg>
  );
}

export default function PayPalLogo({
  className,
  markClassName = 'h-6 w-auto',
  wordmark = true,
}: {
  className?: string;
  markClassName?: string;
  wordmark?: boolean;
}) {
  return (
    <span
      className={cn('inline-flex items-center gap-1.5', className)}
      role="img"
      aria-label="PayPal"
    >
      <PayPalMark className={markClassName} />
      {wordmark && (
        <span className="text-[1.1em] font-extrabold italic leading-none tracking-[-0.05em]">
          <span className="text-[#003087]">Pay</span>
          <span className="text-[#009cde]">Pal</span>
        </span>
      )}
    </span>
  );
}
