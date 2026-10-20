import { useState } from 'react';
import { ChevronDown, Loader2, Phone, PhoneOff, Radio } from 'lucide-react';
import { inputClass } from '@/components/checkout/styles';

interface Props {
  deviceId: string;
  token: string;
  activeCall: boolean | null;
  observedAt: string | null;
  reporting: boolean;
  error: string | null;
  onTokenChange: (token: string) => void;
  onReport: (activeCall: boolean) => void;
  /** True inside the Android app, where telephony state is read natively. */
  automatic?: boolean;
}

const callButtonClass =
  'inline-flex min-h-10 w-full items-center justify-center gap-1.5 rounded-full bg-amber-600 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-amber-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto';

const endButtonClass =
  'inline-flex min-h-10 w-full items-center justify-center gap-1.5 rounded-full border border-[#cbd2d6] bg-white px-4 py-2 text-xs font-semibold text-[#001435] transition-colors hover:bg-[#f5f7fa] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0070e0] disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto';

function statusText(activeCall: boolean | null, paired: boolean): string {
  if (!paired) return 'Setup needed';
  if (activeCall === true) return 'Call in progress';
  if (activeCall === false) return 'No active call';
  return 'No call signal yet';
}

function observedLabel(observedAt: string | null): string | null {
  if (observedAt === null) return null;
  const parsed = new Date(observedAt);
  if (Number.isNaN(parsed.getTime())) return null;
  return `Reported at ${parsed.toLocaleTimeString()}`;
}

/**
 * Manual call-state signal for phone browsers. Browsers cannot read telephony
 * state, so this panel lets the user declare it; the backend still decides
 * whether the stored observation is trustworthy and fresh.
 */
export default function CallStatePanel({
  deviceId,
  token,
  activeCall,
  observedAt,
  reporting,
  error,
  onTokenChange,
  onReport,
  automatic = false,
}: Props) {
  const [editingToken, setEditingToken] = useState(token.trim() === '');
  const [tokenDraft, setTokenDraft] = useState(token);
  const paired = automatic || token.trim() !== '';
  const observed = observedLabel(observedAt);
  const status = statusText(activeCall, paired);

  return (
    <>
      <details className="group rounded-2xl border border-[#e1e6eb] bg-white shadow-sm">
      <summary className="flex cursor-pointer list-none flex-wrap items-center justify-between gap-x-2 gap-y-1 p-4 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0070e0] [&::-webkit-details-marker]:hidden">
        <span className="flex min-w-0 items-center gap-2 text-sm font-semibold text-[#001435]">
          <Radio className="h-4 w-4 shrink-0 text-[#0070e0]" aria-hidden="true" />
          <span className="truncate">Phone call signal</span>
          <span
            className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${
              automatic ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
            }`}
          >
            {automatic ? 'Automatic' : 'Demo'}
          </span>
        </span>
        <span className="ml-auto flex shrink-0 items-center gap-1.5">
          <span
            className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
              !paired
                ? 'bg-amber-100 text-amber-800'
                : activeCall === true
                  ? 'bg-amber-100 text-amber-800'
                  : activeCall === false
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-muted text-muted-foreground'
            }`}
          >
            {status}
          </span>
          <ChevronDown
            className="h-4 w-4 text-muted-foreground transition-transform group-open:rotate-180"
            aria-hidden="true"
          />
        </span>
      </summary>

      <div className="border-t border-[#e1e6eb] px-4 pb-4 pt-3">
        <p className="text-xs text-[#6c7378]">
          {automatic
            ? 'Detected automatically on this device while GuardPay is open. Only the on/off call state is used; no audio, number, or contacts are read. The next payment check uses it for five minutes.'
            : "A browser cannot read your phone's call state. Report it here; the next payment's risk check uses it for five minutes."}
        </p>

        {!automatic && (
          <div className="mt-2.5 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
            <button
              type="button"
              onClick={() => onReport(true)}
              disabled={reporting || !paired}
              className={callButtonClass}
            >
              {reporting ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
              ) : (
                <Phone className="h-3.5 w-3.5" aria-hidden="true" />
              )}
              Call active
            </button>
            <button
              type="button"
              onClick={() => onReport(false)}
              disabled={reporting || !paired}
              className={endButtonClass}
            >
              <PhoneOff className="h-3.5 w-3.5" aria-hidden="true" />
              Call ended
            </button>
            {paired && !editingToken && (
              <span className="text-[11px] text-[#6c7378]">
                Paired ·{' '}
                <button
                  type="button"
                  onClick={() => setEditingToken(true)}
                  className="font-medium text-[#0070e0] underline underline-offset-2"
                >
                  change token
                </button>
              </span>
            )}
          </div>
        )}

        {!automatic && editingToken && (
          <div className="mt-2.5 flex flex-col gap-2 sm:flex-row sm:items-center">
            <label htmlFor="device-pairing-token" className="sr-only">
              Device pairing token
            </label>
            <input
              id="device-pairing-token"
              type="password"
              autoComplete="off"
              value={tokenDraft}
              onChange={(event) => setTokenDraft(event.target.value)}
              placeholder="Device pairing token"
              className={`${inputClass} sm:max-w-xs`}
            />
            <button
              type="button"
              onClick={() => {
                onTokenChange(tokenDraft);
                setEditingToken(false);
              }}
              className={endButtonClass}
            >
              Save token
            </button>
          </div>
        )}

        {error !== null && (
          <p
            className="mt-2.5 rounded-lg border border-red-200 bg-red-50 p-2.5 text-xs text-red-800"
            role="alert"
          >
            {error}
          </p>
        )}

        <details className="group/device mt-2">
          <summary className="flex w-fit cursor-pointer list-none items-center gap-1 text-[11px] text-muted-foreground [&::-webkit-details-marker]:hidden">
            <ChevronDown
              className="h-3 w-3 transition-transform group-open/device:rotate-180"
              aria-hidden="true"
            />
            Device details
          </summary>
          <p className="mt-1 break-all font-mono text-[10px] text-muted-foreground">
            {deviceId}
            {observed !== null && <> · {observed}</>}
          </p>
        </details>
      </div>
      </details>
    </>
  );
}
