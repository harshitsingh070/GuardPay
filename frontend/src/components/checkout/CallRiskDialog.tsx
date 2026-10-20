import { useEffect, useRef } from 'react';
import { PhoneCall, ShieldAlert } from 'lucide-react';

interface Props {
  open: boolean;
  onCancel: () => void;
  onContinue: () => void;
}

/**
 * Blocking warning shown whenever the payment page sees an active call.
 * It intentionally requires an explicit choice instead of a dismissible toast:
 * a fraudster often stays on the line while pressuring the victim to pay.
 */
export default function CallRiskDialog({ open, onCancel, onContinue }: Props) {
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
    cancelRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center bg-[#001435]/60 p-4 backdrop-blur-sm sm:items-center">
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="call-risk-title"
        aria-describedby="call-risk-description"
        className="max-h-[calc(100vh-2rem)] w-full max-w-md overflow-y-auto overscroll-contain rounded-2xl border-t-4 border-red-500 bg-white shadow-2xl"
      >
        <div className="p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-red-100 text-red-600">
              <PhoneCall className="h-6 w-6" aria-hidden="true" />
            </span>
            <div className="min-w-0">
              <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-red-600">
                Call detected
              </p>
              <h2 id="call-risk-title" className="mt-0.5 text-xl font-bold text-[#001435]">
                You are on a phone call
              </h2>
            </div>
          </div>

          <p id="call-risk-description" className="mt-3 text-sm text-[#6c7378]">
            The person on the call may be a fraudster. Scammers often stay on the line and
            pressure you to pay before you have a chance to verify who they are.
          </p>

          <ul className="mt-4 space-y-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
            <li className="flex items-start gap-2">
              <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              Hang up and call the person or company back using a number you find yourself.
            </li>
            <li className="flex items-start gap-2">
              <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              Never share a one-time code, password, or remote-access access while paying.
            </li>
          </ul>

          <div className="mt-5 grid gap-2 sm:grid-cols-2">
            <button
              ref={cancelRef}
              type="button"
              onClick={onCancel}
              className="inline-flex min-h-12 w-full items-center justify-center rounded-full bg-red-600 px-5 py-3 text-sm font-bold text-white shadow-sm transition-colors hover:bg-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
            >
              Cancel payment
            </button>
            <button
              type="button"
              onClick={onContinue}
              className="inline-flex min-h-12 w-full items-center justify-center rounded-full border border-[#cbd2d6] bg-white px-5 py-3 text-sm font-bold text-[#001435] transition-colors hover:bg-[#f5f7fa] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0070e0]"
            >
              Continue anyway
            </button>
          </div>

          <p className="mt-3 text-center text-[11px] text-[#6c7378]">
            GuardPay cannot identify who is on the call. It only adds the call as extra risk.
          </p>
        </div>
      </div>
    </div>
  );
}
