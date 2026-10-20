import { useCallback, useRef, useState } from 'react';
import { ApiRequestError } from '@/services/api-client';
import { getCheckoutService } from '@/services/checkout-service';
import {
  EMPTY_DRAFT,
  toGuardedPaymentRequest,
  validateDraft,
  type CheckoutContext,
  type CheckoutDraft,
  type CheckoutPhase,
  type CheckoutService,
} from '@/types/checkout';
import type {
  GuardedPaymentResponse,
  VerificationConfirmResponse,
} from '@/types/payment';
import type { PayPalCaptureResponse } from '@/types/paypal';

export type CheckoutFailureStage = 'submission' | 'capture' | 'verification' | null;

export interface CheckoutState {
  phase: CheckoutPhase;
  result: GuardedPaymentResponse | null;
  capture: PayPalCaptureResponse | null;
  verification: VerificationConfirmResponse | null;
  error: string | null;
  failureStage: CheckoutFailureStage;
  completedAt: string | null;
  submittedDraft: CheckoutDraft | null;
}

const INITIAL_STATE: CheckoutState = {
  phase: 'READY',
  result: null,
  capture: null,
  verification: null,
  error: null,
  failureStage: null,
  completedAt: null,
  submittedDraft: null,
};

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `gp-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function phaseFor(result: GuardedPaymentResponse): CheckoutPhase {
  if (result.risk.recommendedAction === 'STOP') return 'RISK_BLOCKED';
  if (result.risk.recommendedAction === 'VERIFY') return 'RISK_VERIFICATION_REQUIRED';
  return result.paypalOrder !== null ? 'PAYPAL_APPROVAL_REQUIRED' : 'RISK_ALLOWED';
}

function failureMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message;
  return 'Something went wrong while processing this payment. Please try again.';
}

/**
 * Checkout state machine. The only sources of truth are the backend response
 * and explicit user actions; no success state is guessed in the browser.
 *
 * The idempotency key is derived from the submitted draft, so retrying the
 * same attempt reuses the key while editing the payment produces a new one.
 */
export function useCheckout(service: CheckoutService = getCheckoutService()) {
  const [state, setState] = useState<CheckoutState>(INITIAL_STATE);
  const inFlight = useRef(false);
  const keyRef = useRef<string | null>(null);
  const draftSignatureRef = useRef<string | null>(null);

  const submit = useCallback(
    async (draft: CheckoutDraft, context: CheckoutContext = {}) => {
      if (inFlight.current) return;

      const validationError = validateDraft(draft);
      if (validationError !== null) {
        setState((previous) => ({
          ...previous,
          phase: 'READY',
          error: validationError,
          failureStage: null,
        }));
        return;
      }

      // A new device/call-state observation is new payment context, so it must
      // not replay the previous attempt's stored decision.
      const signature = JSON.stringify({
        draft,
        deviceId: context.deviceId ?? null,
        deviceStateKey: context.deviceStateKey ?? null,
      });
      if (signature !== draftSignatureRef.current || keyRef.current === null) {
        keyRef.current = newIdempotencyKey();
        draftSignatureRef.current = signature;
      }

      inFlight.current = true;
      setState({
        ...INITIAL_STATE,
        phase: 'CHECKING_RISK',
        submittedDraft: { ...draft },
      });

      try {
        const result = await service.createPayment(
          toGuardedPaymentRequest(draft, context.deviceId),
          keyRef.current,
        );
        const phase = phaseFor(result);
        setState({
          phase,
          result,
          capture: null,
          verification: null,
          error: null,
          failureStage: null,
          completedAt: null,
          submittedDraft: { ...draft },
        });
      } catch (error) {
        setState({
          phase: 'FAILED',
          result: null,
          capture: null,
          verification: null,
          error: failureMessage(error),
          failureStage: 'submission',
          completedAt: null,
          submittedDraft: { ...draft },
        });
      } finally {
        inFlight.current = false;
      }
    },
    [service],
  );

  const capture = useCallback(async () => {
    if (inFlight.current) return;
    const order = state.result?.paypalOrder;
    if (!order) return;

    inFlight.current = true;
    setState((previous) => ({
      ...previous,
      phase: 'CAPTURING',
      error: null,
      failureStage: null,
    }));

    try {
      const captureResult = await service.capturePayment(order.orderId);
      if (captureResult.paymentStatus !== 'COMPLETED') {
        setState((previous) => ({
          ...previous,
          phase: 'FAILED',
          capture: captureResult,
          error: `GuardPay recorded the payment as ${captureResult.paymentStatus}. No payment was completed.`,
          failureStage: 'capture',
        }));
        return;
      }
      setState((previous) => ({
        ...previous,
        phase: 'COMPLETED',
        capture: captureResult,
        error: null,
        failureStage: null,
        completedAt: new Date().toISOString(),
      }));
    } catch (error) {
      setState((previous) => ({
        ...previous,
        phase: 'FAILED',
        error: failureMessage(error),
        failureStage: 'capture',
      }));
    } finally {
      inFlight.current = false;
    }
  }, [service, state.result]);

  const confirmVerification = useCallback(
    async (code: string) => {
      if (inFlight.current) return;
      const current = state.result;
      if (!current || current.verification === null) return;

      inFlight.current = true;
      setState((previous) => ({
        ...previous,
        phase: 'VERIFYING',
        error: null,
        failureStage: null,
      }));

      try {
        const verification = await service.confirmVerification(current.paymentId, code);
        if (verification.verificationStatus === 'VERIFIED' && verification.paypalOrder !== null) {
          const order = verification.paypalOrder;
          setState((previous) => ({
            ...previous,
            phase: 'PAYPAL_APPROVAL_REQUIRED',
            verification,
            result:
              previous.result === null
                ? null
                : {
                    ...previous.result,
                    paymentStatus: verification.paymentStatus,
                    paypalOrder: order,
                    verification:
                      previous.result.verification === null
                        ? null
                        : {
                            ...previous.result.verification,
                            status: 'VERIFIED',
                            attemptsRemaining: verification.attemptsRemaining,
                            challengeCode: undefined,
                          },
                  },
            error: null,
            failureStage: null,
          }));
          return;
        }
        if (
          verification.verificationStatus === 'FAILED' ||
          verification.verificationStatus === 'EXPIRED'
        ) {
          setState((previous) => ({
            ...previous,
            phase: 'FAILED',
            verification,
            error: verification.message,
            failureStage: 'verification',
          }));
          return;
        }
        setState((previous) => ({
          ...previous,
          phase: 'RISK_VERIFICATION_REQUIRED',
          verification,
          error: verification.message,
          failureStage: null,
        }));
      } catch (error) {
        setState((previous) => ({
          ...previous,
          phase: 'RISK_VERIFICATION_REQUIRED',
          error: failureMessage(error),
          failureStage: null,
        }));
      } finally {
        inFlight.current = false;
      }
    },
    [service, state.result],
  );

  const reset = useCallback(() => {
    keyRef.current = null;
    draftSignatureRef.current = null;
    setState(INITIAL_STATE);
  }, []);

  const clearError = useCallback(() => {
    setState((previous) => ({
      ...previous,
      error: null,
      phase: previous.result === null ? 'READY' : previous.phase,
      failureStage: null,
    }));
  }, []);

  return {
    ...state,
    captureResult: state.capture,
    draft: state.submittedDraft ?? EMPTY_DRAFT,
    submit,
    capture,
    confirmVerification,
    reset,
    clearError,
  };
}
