import { useState } from 'react';
import { Lock, ShieldCheck } from 'lucide-react';
import CallRiskDialog, { type CallRiskVariant } from '@/components/checkout/CallRiskDialog';
import DecisionPanel from '@/components/checkout/DecisionPanel';
import DemoPresets from '@/components/checkout/DemoPresets';
import DeveloperDetails from '@/components/checkout/DeveloperDetails';
import FailurePanel from '@/components/checkout/FailurePanel';
import MockBanner from '@/components/checkout/MockBanner';
import OrderSummary from '@/components/checkout/OrderSummary';
import PaymentDetailsForm from '@/components/checkout/PaymentDetailsForm';
import PayPalApprovalPanel from '@/components/checkout/PayPalApprovalPanel';
import PayPalLogo from '@/components/checkout/PayPalLogo';
import ReceiptPanel from '@/components/checkout/ReceiptPanel';
import SecurityCheckPanel from '@/components/checkout/SecurityCheckPanel';
import SecurityIndicator from '@/components/checkout/SecurityIndicator';
import VerificationPanel from '@/components/checkout/VerificationPanel';
import { primaryButtonClass, secondaryButtonClass } from '@/components/checkout/styles';
import { usePageTitle } from '@/hooks/usePageTitle';
import { useCheckout } from '@/hooks/useCheckout';
import { useDeviceSignal } from '@/hooks/useDeviceSignal';
import { isMockCheckout } from '@/services/checkout-service';
import {
  EMPTY_DRAFT,
  validateDraft,
  type CheckoutDraft,
  type CheckoutService,
} from '@/types/checkout';

const HERO_MESSAGE =
  'You need to pay this invoice immediately to avoid account suspension. Do not contact anyone else. This is the official support department. Use the payment link below.';
const HERO_URL = 'https://example-suspicious-domain.test/pay';

/** Presets fill payment context only — never a risk verdict. */
const PRESETS: Record<'safe' | 'suspicious', CheckoutDraft> = {
  safe: {
    amount: '45',
    recipient: 'Known Co',
    recipientEmail: 'billing@known-co.example',
    merchant: 'Known Shop',
    description: 'Monthly software subscription invoice for $45.',
    url: '',
  },
  suspicious: {
    amount: '2499',
    recipient: 'Fresh Stranger',
    recipientEmail: 'stranger@example.com',
    merchant: 'Unknown Shop',
    description: HERO_MESSAGE,
    url: HERO_URL,
  },
};

/**
 * Primary GuardPay experience: a PayPal-style Sandbox checkout whose
 * ALLOW / VERIFY / STOP result is always produced server-side.
 */
export default function GuardedPaymentPage({ service }: { service?: CheckoutService }) {
  usePageTitle('Pay securely');
  const checkout = useCheckout(service);
  const device = useDeviceSignal({ autoProvision: true });
  const [draft, setDraft] = useState<CheckoutDraft>(EMPTY_DRAFT);
  const [callWarning, setCallWarning] = useState<CallRiskVariant | null>(null);

  const phase = checkout.phase;
  const result = checkout.result;
  const capture = checkout.captureResult;
  const displayDraft = checkout.submittedDraft ?? draft;
  const showForm =
    phase === 'READY' || (phase === 'FAILED' && checkout.failureStage === 'submission');
  const captureFailed = phase === 'FAILED' && checkout.failureStage === 'capture';
  const showApproval =
    result?.paypalOrder != null &&
    (phase === 'PAYPAL_APPROVAL_REQUIRED' || phase === 'CAPTURING' || captureFailed);

  function handleChange(patch: Partial<CheckoutDraft>) {
    setDraft((previous) => ({ ...previous, ...patch }));
  }

  function submitPayment() {
    void checkout.submit(draft, {
      deviceId: device.deviceId,
      deviceFingerprint: device.fingerprint,
      deviceStateKey: device.stateKey,
    });
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (validateDraft(draft) === null) {
      // The Android shell detects calls automatically and warns only when one
      // is active. A browser cannot read telephony state, so the web build
      // asks the user before paying instead.
      if (device.automatic) {
        if (device.activeCall === true) {
          setCallWarning('detected');
          return;
        }
      } else {
        setCallWarning('self-check');
        return;
      }
    }
    submitPayment();
  }

  function handleCancel() {
    checkout.reset();
    setDraft(EMPTY_DRAFT);
  }

  function handleCallCancel() {
    setCallWarning(null);
  }

  function handleCallContinue() {
    setCallWarning(null);
    submitPayment();
  }

  function applyPreset(kind: 'safe' | 'suspicious') {
    checkout.reset();
    setDraft(PRESETS[kind]);
  }

  const recipient = draft.recipient.trim() || displayDraft.recipient.trim();

  return (
    <section className="mx-auto w-full max-w-5xl">
      <div className="overflow-hidden rounded-2xl border border-[#e1e6eb] bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#e1e6eb] px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2">
            <PayPalLogo className="text-lg" markClassName="h-6 w-auto" />
            <span className="rounded-full bg-[#eaf2ff] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[#003087]">
              Sandbox
            </span>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden items-center gap-1 text-xs text-[#6c7378] sm:flex">
              <Lock className="h-3.5 w-3.5" aria-hidden="true" />
              Secure checkout
            </span>
            <SecurityIndicator />
          </div>
        </div>

        <div className="bg-[#f5f7fa] p-4 sm:p-6">
          <header className="mb-5 flex flex-col gap-3">
            <div className="min-w-0">
              <p className="text-xs font-semibold uppercase tracking-wide text-[#0070e0]">
                GuardPay protected payment
              </p>
              <h1 className="mt-1 break-words text-2xl font-bold text-[#001435] sm:text-3xl">
                {recipient === '' ? 'Pay securely' : `Pay ${recipient}`}
              </h1>
              <p className="mt-1 max-w-2xl text-sm text-[#6c7378]">
                Review the payment and let GuardPay check it before PayPal Sandbox checkout.
              </p>
            </div>
          </header>

          {isMockCheckout && <MockBanner />}

          <div
            data-testid="checkout-grid"
            className="grid gap-5 sm:gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start"
          >
            <div className="min-w-0 space-y-5 sm:space-y-6 lg:col-start-1 lg:row-start-1">
              {showForm && (
                <>
                  <div className="rounded-2xl border border-[#e1e6eb] bg-white p-4 shadow-sm sm:p-6">
                    <PaymentDetailsForm
                      draft={draft}
                      onChange={handleChange}
                      onSubmit={handleSubmit}
                      onCancel={handleCancel}
                      disabled={false}
                      error={phase === 'FAILED' ? null : checkout.error}
                    />
                  </div>
                  <DemoPresets onSelect={applyPreset} onReset={handleCancel} />
                </>
              )}

              {phase === 'CHECKING_RISK' && <SecurityCheckPanel draft={displayDraft} />}
            </div>

            <OrderSummary
              draft={displayDraft}
              paid={phase === 'COMPLETED'}
              className="lg:col-start-2 lg:row-start-1"
            />

            <div className="min-w-0 space-y-5 sm:space-y-6 lg:col-start-1 lg:row-start-2">
              {result !== null && phase !== 'COMPLETED' && <DecisionPanel result={result} />}

              {(phase === 'RISK_VERIFICATION_REQUIRED' || phase === 'VERIFYING') &&
                result?.verification != null && (
                  <VerificationPanel
                    verification={{
                      ...result.verification,
                      attemptsRemaining:
                        checkout.verification?.attemptsRemaining ??
                        result.verification.attemptsRemaining,
                    }}
                    submitting={phase === 'VERIFYING'}
                    error={checkout.error}
                    onSubmit={(code) => void checkout.confirmVerification(code)}
                    onCancel={handleCancel}
                  />
                )}

              {phase === 'RISK_BLOCKED' && (
                <button type="button" onClick={handleCancel} className={secondaryButtonClass}>
                  Start a new payment
                </button>
              )}

              {showApproval && result?.paypalOrder != null && (
                <PayPalApprovalPanel
                  order={result.paypalOrder}
                  capturing={phase === 'CAPTURING'}
                  error={null}
                  onCapture={() => void checkout.capture()}
                  onCancel={handleCancel}
                />
              )}

              {phase === 'COMPLETED' && result !== null && capture !== null && (
                <>
                  <ReceiptPanel
                    result={result}
                    capture={capture}
                    completedAt={checkout.completedAt}
                    draft={displayDraft}
                  />
                  <div className="flex flex-wrap gap-2">
                    <button type="button" onClick={handleCancel} className={primaryButtonClass}>
                      Make another payment
                    </button>
                  </div>
                </>
              )}

              {phase === 'FAILED' && (
                <FailurePanel
                  message={
                    checkout.error ??
                    (checkout.failureStage === 'verification'
                      ? 'Verification did not succeed.'
                      : checkout.failureStage === 'capture'
                        ? 'PayPal did not confirm this payment.'
                        : 'The security check did not finish.')
                  }
                  retryLabel={null}
                  onRetry={null}
                  onStartOver={handleCancel}
                />
              )}

              {result !== null && (
                <DeveloperDetails phase={phase} result={result} capture={capture} />
              )}
            </div>
          </div>

          <footer className="mt-6 space-y-3 border-t border-[#e1e6eb] pt-4 text-xs text-[#6c7378]">
            <p>
              GuardPay is a reference integration layer demonstrating how AI-assisted payment safety
              could integrate with PayPal payment authorization flows. It is a hackathon prototype —
              not production fraud detection, not part of the official PayPal app, and not endorsed by
              PayPal.
            </p>
            <p className="flex items-start gap-1.5">
              <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>
                GuardPay checks are based on the payment context you provide and your GuardPay history.
                They do not inspect your PayPal account.
              </span>
            </p>
          </footer>
        </div>
      </div>

      <CallRiskDialog
        open={callWarning !== null}
        variant={callWarning ?? 'detected'}
        onCancel={handleCallCancel}
        onContinue={handleCallContinue}
      />
    </section>
  );
}
