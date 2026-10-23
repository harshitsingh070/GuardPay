import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import GuardedPaymentPage from '@/pages/GuardedPaymentPage';
import { ApiRequestError } from '@/services/api-client';
import type { CheckoutService } from '@/types/checkout';
import {
  blockedResponse,
  completedCapture,
  deferred,
  failedConfirmation,
  paymentResponse,
  pendingConfirmation,
  verifiedConfirmation,
  verifyResponse,
} from '@/test/fixtures';
import type { GuardedPaymentResponse } from '@/types/payment';

function setup() {
  const createPayment = vi.fn<CheckoutService['createPayment']>();
  const capturePayment = vi.fn<CheckoutService['capturePayment']>();
  const confirmVerification = vi.fn<CheckoutService['confirmVerification']>();
  const service: CheckoutService = { createPayment, capturePayment, confirmVerification };
  return { createPayment, capturePayment, confirmVerification, service };
}

async function fillAmount(user: ReturnType<typeof userEvent.setup>, amount = '45') {
  await user.type(screen.getByLabelText(/Amount \(USD\)/), amount);
  const email = screen.getByLabelText('Recipient PayPal email');
  if ((email as HTMLInputElement).value === '') {
    await user.type(email, 'known@example.com');
  }
}

async function continuePastCallCheck(user: ReturnType<typeof userEvent.setup>) {
  const dialog = screen.queryByRole('alertdialog');
  if (dialog !== null) {
    await user.click(screen.getByRole('button', { name: 'No, continue payment' }));
  }
}

describe('GuardedPaymentPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    delete window.GuardPayNative;
  });

  it('renders the checkout form with accessible payment fields and PayPal Sandbox', async () => {
    const { service } = setup();
    const user = userEvent.setup();
    render(<GuardedPaymentPage service={service} />);

    expect(screen.getByLabelText(/Amount \(USD\)/)).toBeInTheDocument();
    expect(screen.getByLabelText('Recipient')).toBeInTheDocument();
    expect(screen.getByLabelText('Recipient PayPal email')).toBeInTheDocument();
    expect(screen.getByLabelText('Merchant')).toBeInTheDocument();
    expect(screen.getByLabelText(/Payment description/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Payment link/)).toBeInTheDocument();
    expect(screen.getAllByText('PayPal Sandbox').length).toBeGreaterThan(0);
    expect(screen.getByRole('complementary', { name: 'Order summary' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Pay securely$/ }));
    expect(
      screen.getByText('Enter a payment amount greater than zero.'),
    ).toBeInTheDocument();
    expect(service.createPayment).not.toHaveBeenCalled();
  });

  it('runs ALLOW -> approval -> capture and only then shows the receipt', async () => {
    const { createPayment, capturePayment, service } = setup();
    const user = userEvent.setup();
    const pending = deferred<GuardedPaymentResponse>();
    createPayment.mockReturnValue(pending.promise);
    capturePayment.mockResolvedValue(completedCapture());

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.type(screen.getByLabelText('Recipient'), 'Known Co');
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);

    expect(screen.getByText('Checking this payment')).toBeInTheDocument();
    expect(
      screen.getByRole('status', { name: 'Checking payment safety' }),
    ).toBeInTheDocument();

    await act(async () => {
      pending.resolve(paymentResponse());
    });

    expect(await screen.findByText('Payment allowed')).toBeInTheDocument();
    expect(createPayment).toHaveBeenCalledTimes(1);
    expect(createPayment.mock.calls[0]?.[0]).toMatchObject({ amount: 45, currency: 'USD' });
    expect(createPayment.mock.calls[0]?.[0].deviceId).toBeTruthy();
    expect(typeof createPayment.mock.calls[0]?.[1]).toBe('string');

    const approvalLink = screen.getByRole('link', { name: /Approve in PayPal Sandbox/ });
    expect(approvalLink).toHaveAttribute(
      'href',
      'https://www.sandbox.paypal.com/checkoutnow?token=ORDER-1',
    );
    expect(screen.queryByText('Payment complete')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /I approved/ }));

    expect(await screen.findByText('Payment complete')).toBeInTheDocument();
    expect(capturePayment).toHaveBeenCalledWith('ORDER-1');
    expect(screen.getAllByText('$45.00').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('CAPTURE-1').length).toBeGreaterThanOrEqual(1);
  });

  it('keeps the approval link and shows the reason when capture is not approved', async () => {
    const { createPayment, capturePayment, service } = setup();
    const user = userEvent.setup();
    createPayment.mockResolvedValue(paymentResponse());
    capturePayment.mockRejectedValue(
      new ApiRequestError(
        409,
        'This PayPal order has not been approved yet. Approve it in the PayPal Sandbox window, then capture again.',
      ),
    );

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);
    await user.click(await screen.findByRole('button', { name: /I approved/ }));

    expect(await screen.findByText('Payment not completed')).toBeInTheDocument();
    expect(screen.getByText(/has not been approved yet/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Approve in PayPal Sandbox/ })).toBeInTheDocument();
  });

  it('shows VERIFY without creating PayPal approval controls', async () => {
    const { createPayment, capturePayment, service } = setup();
    const user = userEvent.setup();
    createPayment.mockResolvedValue(verifyResponse());

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);

    expect(await screen.findByText('Verification required')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Approve in PayPal Sandbox/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /I approved/ })).toBeNull();
    expect(screen.getByText('Confirm this payment')).toBeInTheDocument();
    expect(screen.getByLabelText('Verification code')).toBeInTheDocument();
    expect(capturePayment).not.toHaveBeenCalled();
  });

  it('reveals PayPal approval only after the code is verified', async () => {
    const { createPayment, capturePayment, confirmVerification, service } = setup();
    const user = userEvent.setup();
    createPayment.mockResolvedValue(verifyResponse());
    confirmVerification.mockResolvedValue(verifiedConfirmation());

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);
    expect(await screen.findByText('Confirm this payment')).toBeInTheDocument();
    expect(capturePayment).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('Verification code'), '123456');
    await user.click(screen.getByRole('button', { name: /Confirm verification/ }));

    expect(
      await screen.findByRole('link', { name: /Approve in PayPal Sandbox/ }),
    ).toBeInTheDocument();
    expect(confirmVerification).toHaveBeenCalledWith('pay-1', '123456');
    expect(capturePayment).not.toHaveBeenCalled();
  });

  it('keeps the challenge open after a wrong code', async () => {
    const { createPayment, confirmVerification, service } = setup();
    const user = userEvent.setup();
    createPayment.mockResolvedValue(verifyResponse());
    confirmVerification.mockResolvedValue(pendingConfirmation());

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);
    await screen.findByText('Confirm this payment');

    await user.type(screen.getByLabelText('Verification code'), '000000');
    await user.click(screen.getByRole('button', { name: /Confirm verification/ }));

    expect(
      await screen.findByText('That code did not match. Please try again.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Approve in PayPal Sandbox/ })).toBeNull();
  });

  it('closes the checkout when verification is exhausted', async () => {
    const { createPayment, confirmVerification, service } = setup();
    const user = userEvent.setup();
    createPayment.mockResolvedValue(verifyResponse());
    confirmVerification.mockResolvedValue(failedConfirmation());

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);
    await screen.findByText('Confirm this payment');

    await user.type(screen.getByLabelText('Verification code'), '000000');
    await user.click(screen.getByRole('button', { name: /Confirm verification/ }));

    expect(await screen.findByText('Payment not completed')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Approve in PayPal Sandbox/ })).toBeNull();
  });

  it('shows STOP without creating PayPal approval controls', async () => {
    const { createPayment, service } = setup();
    const user = userEvent.setup();
    createPayment.mockResolvedValue(blockedResponse());

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);

    expect(await screen.findByText('Payment blocked')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Approve in PayPal Sandbox/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /I approved/ })).toBeNull();
    expect(screen.getByText(/No PayPal order was created/)).toBeInTheDocument();
  });

  it('recovers from a submission failure and retries with a new idempotency key after edits', async () => {
    const { createPayment, service } = setup();
    const user = userEvent.setup();
    createPayment
      .mockRejectedValueOnce(
        new ApiRequestError(0, 'Unable to reach GuardPay. Check your connection and try again.'),
      )
      .mockResolvedValueOnce(paymentResponse());

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await continuePastCallCheck(user);

    expect(await screen.findByText('Payment not completed')).toBeInTheDocument();
    expect(
      screen.getByText('Unable to reach GuardPay. Check your connection and try again.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Amount \(USD\)/)).toHaveValue(45);

    const amount = screen.getByLabelText(/Amount \(USD\)/);
    await user.clear(amount);
    await user.type(amount, '46');
    await user.click(screen.getByRole('button', { name: /^Pay \$46\.00$/ }));
    await continuePastCallCheck(user);

    expect(await screen.findByText('Payment allowed')).toBeInTheDocument();
    expect(createPayment).toHaveBeenCalledTimes(2);
    expect(createPayment.mock.calls[0]?.[1]).not.toBe(createPayment.mock.calls[1]?.[1]);
  });

  it('asks about a call on the web before paying', async () => {
    const { createPayment, service } = setup();
    createPayment.mockResolvedValue(paymentResponse());
    const user = userEvent.setup();

    render(<GuardedPaymentPage service={service} />);
    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));

    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(screen.getByText('Are you on a phone call?')).toBeInTheDocument();
    expect(createPayment).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Yes, cancel payment' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(createPayment).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await user.click(screen.getByRole('button', { name: 'No, continue payment' }));

    expect(createPayment).toHaveBeenCalledTimes(1);
  });

  it('warns only when paying during a call and continues on request', async () => {
    const { createPayment, service } = setup();
    createPayment.mockResolvedValue(paymentResponse());
    const user = userEvent.setup();
    window.GuardPayNative = {
      getDeviceId: () => '11111111-1111-4111-8111-111111111111',
      getApiBaseUrl: () => 'http://127.0.0.1:8080',
      isCallStateKnown: () => true,
      isCallActive: () => true,
      getLastObservedAt: () => new Date().toISOString(),
      openUrl: vi.fn(),
    };

    render(<GuardedPaymentPage service={service} />);

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();

    await fillAmount(user);
    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));

    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(screen.getByText('You are on a phone call')).toBeInTheDocument();
    expect(createPayment).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Cancel payment' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Amount \(USD\)/)).toHaveValue(45);
    expect(createPayment).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /^Pay \$45\.00$/ }));
    await user.click(screen.getByRole('button', { name: 'Continue anyway' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(createPayment).toHaveBeenCalledTimes(1);
  });

  it('keeps the form and summary usable at a narrow viewport', async () => {
    const { service } = setup();
    const user = userEvent.setup();
    window.innerWidth = 375;
    render(<GuardedPaymentPage service={service} />);

    expect(screen.getByLabelText(/Amount \(USD\)/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Pay securely$/ })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Order summary' })).toBeInTheDocument();
    expect(screen.getByTestId('checkout-grid')).toHaveClass(
      'lg:grid-cols-[minmax(0,1fr)_22rem]',
    );

    await fillAmount(user, '12');
    expect(screen.getByRole('button', { name: /^Pay \$12\.00$/ })).toBeInTheDocument();
  });
});
