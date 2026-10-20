import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useCheckout } from '@/hooks/useCheckout';
import { ApiRequestError } from '@/services/api-client';
import type { CheckoutDraft, CheckoutService } from '@/types/checkout';
import {
  completedCapture,
  deferred,
  failedConfirmation,
  paymentResponse,
  pendingConfirmation,
  verifiedConfirmation,
  verifyResponse,
} from '@/test/fixtures';

const DRAFT: CheckoutDraft = {
  amount: '45',
  recipient: 'Known Co',
  merchant: 'Known Shop',
  description: 'Invoice 1042',
  url: '',
};

function setup() {
  const createPayment = vi.fn<CheckoutService['createPayment']>();
  const capturePayment = vi.fn<CheckoutService['capturePayment']>();
  const confirmVerification = vi.fn<CheckoutService['confirmVerification']>();
  const service: CheckoutService = { createPayment, capturePayment, confirmVerification };
  const hook = renderHook(() => useCheckout(service));
  return { createPayment, capturePayment, confirmVerification, hook };
}

describe('useCheckout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('ignores a duplicate submit while the first request is in flight', async () => {
    const { createPayment, hook } = setup();
    const pending = deferred<ReturnType<typeof paymentResponse>>();
    createPayment.mockReturnValue(pending.promise);

    await act(async () => {
      const first = hook.result.current.submit(DRAFT);
      const second = hook.result.current.submit(DRAFT);
      pending.resolve(paymentResponse());
      await Promise.all([first, second]);
    });

    expect(createPayment).toHaveBeenCalledTimes(1);
    expect(hook.result.current.phase).toBe('PAYPAL_APPROVAL_REQUIRED');
  });

  it('rotates the idempotency key when the device call state changes', async () => {
    const { createPayment, hook } = setup();
    createPayment.mockResolvedValue(paymentResponse());

    await act(async () => {
      await hook.result.current.submit(DRAFT, {
        deviceId: 'device-1',
        deviceStateKey: 'false:2026-10-20T09:00:00Z',
      });
    });
    await act(async () => {
      await hook.result.current.submit(DRAFT, {
        deviceId: 'device-1',
        deviceStateKey: 'true:2026-10-20T09:01:00Z',
      });
    });

    expect(createPayment.mock.calls[0]?.[0].deviceId).toBe('device-1');
    expect(createPayment.mock.calls[0]?.[1]).not.toBe(createPayment.mock.calls[1]?.[1]);
  });

  it('reuses the idempotency key for a retried draft and rotates it after edits', async () => {
    const { createPayment, hook } = setup();
    createPayment
      .mockRejectedValueOnce(new ApiRequestError(0, 'Network unavailable'))
      .mockResolvedValueOnce(paymentResponse())
      .mockResolvedValueOnce(paymentResponse());

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });
    expect(hook.result.current.phase).toBe('FAILED');
    expect(hook.result.current.failureStage).toBe('submission');

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });
    expect(createPayment).toHaveBeenCalledTimes(2);

    const firstKey = createPayment.mock.calls[0]?.[1];
    const retryKey = createPayment.mock.calls[1]?.[1];
    expect(retryKey).toBe(firstKey);

    await act(async () => {
      await hook.result.current.submit({ ...DRAFT, amount: '46' });
    });
    const editedKey = createPayment.mock.calls[2]?.[1];
    expect(editedKey).not.toBe(firstKey);
  });

  it('does not reach COMPLETED when PayPal does not confirm the capture', async () => {
    const { createPayment, capturePayment, hook } = setup();
    createPayment.mockResolvedValue(paymentResponse());
    capturePayment.mockResolvedValue({
      ...completedCapture(),
      status: 'PENDING',
      paymentStatus: 'PENDING',
    });

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });
    await act(async () => {
      await hook.result.current.capture();
    });

    expect(hook.result.current.phase).toBe('FAILED');
    expect(hook.result.current.failureStage).toBe('capture');
    expect(hook.result.current.completedAt).toBeNull();
    expect(hook.result.current.error).toContain('PENDING');
  });

  it('keeps a VERIFY decision order-free until verification succeeds', async () => {
    const { createPayment, hook } = setup();
    createPayment.mockResolvedValue(verifyResponse());

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });

    expect(hook.result.current.phase).toBe('RISK_VERIFICATION_REQUIRED');
    expect(hook.result.current.result?.paypalOrder).toBeNull();
    expect(hook.result.current.result?.verification?.status).toBe('PENDING');
  });

  it('moves to PayPal approval only after a verified confirmation', async () => {
    const { createPayment, confirmVerification, hook } = setup();
    createPayment.mockResolvedValue(verifyResponse());
    confirmVerification.mockResolvedValue(verifiedConfirmation());

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });
    await act(async () => {
      await hook.result.current.confirmVerification('123456');
    });

    expect(confirmVerification).toHaveBeenCalledWith('pay-1', '123456');
    expect(hook.result.current.phase).toBe('PAYPAL_APPROVAL_REQUIRED');
    expect(hook.result.current.result?.paypalOrder?.orderId).toBe('ORDER-VERIFIED');
  });

  it('stays on the challenge after a wrong code', async () => {
    const { createPayment, confirmVerification, hook } = setup();
    createPayment.mockResolvedValue(verifyResponse());
    confirmVerification.mockResolvedValue(pendingConfirmation());

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });
    await act(async () => {
      await hook.result.current.confirmVerification('000000');
    });

    expect(hook.result.current.phase).toBe('RISK_VERIFICATION_REQUIRED');
    expect(hook.result.current.verification?.attemptsRemaining).toBe(2);
    expect(hook.result.current.result?.paypalOrder).toBeNull();
  });

  it('fails the attempt when verification is exhausted', async () => {
    const { createPayment, confirmVerification, hook } = setup();
    createPayment.mockResolvedValue(verifyResponse());
    confirmVerification.mockResolvedValue(failedConfirmation());

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });
    await act(async () => {
      await hook.result.current.confirmVerification('000000');
    });

    expect(hook.result.current.phase).toBe('FAILED');
    expect(hook.result.current.failureStage).toBe('verification');
    expect(hook.result.current.result?.paypalOrder).toBeNull();
  });

  it('completes only with a COMPLETED capture from the service', async () => {
    const { createPayment, capturePayment, hook } = setup();
    createPayment.mockResolvedValue(paymentResponse());
    capturePayment.mockResolvedValue(completedCapture());

    await act(async () => {
      await hook.result.current.submit(DRAFT);
    });
    await act(async () => {
      await hook.result.current.capture();
    });

    expect(hook.result.current.phase).toBe('COMPLETED');
    expect(hook.result.current.captureResult?.paymentStatus).toBe('COMPLETED');
    expect(hook.result.current.completedAt).not.toBeNull();
  });
});
