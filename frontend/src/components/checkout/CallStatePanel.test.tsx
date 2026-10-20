import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import CallStatePanel from '@/components/checkout/CallStatePanel';

const BASE_PROPS = {
  deviceId: '11111111-1111-1111-1111-111111111111',
  token: 'dev-token',
  activeCall: null,
  observedAt: null,
  reporting: false,
  error: null,
  onTokenChange: vi.fn(),
  onReport: vi.fn(),
};

describe('CallStatePanel', () => {
  it('reports both call states explicitly', async () => {
    const onReport = vi.fn();
    const user = userEvent.setup();
    render(<CallStatePanel {...BASE_PROPS} onReport={onReport} />);

    await user.click(screen.getByText('Phone call signal'));
    expect(screen.getByText(/cannot read your phone's call state/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Call active/ }));
    expect(onReport).toHaveBeenCalledWith(true);

    await user.click(screen.getByRole('button', { name: /Call ended/ }));
    expect(onReport).toHaveBeenCalledWith(false);
  });

  it('requires a pairing token before reporting', async () => {
    const onTokenChange = vi.fn();
    const user = userEvent.setup();
    render(
      <CallStatePanel {...BASE_PROPS} token="" onTokenChange={onTokenChange} />,
    );

    await user.click(screen.getByText('Phone call signal'));
    expect(screen.getByText('Setup needed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Call active/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Call ended/ })).toBeDisabled();

    await user.type(screen.getByLabelText('Device pairing token'), 'abc');
    await user.click(screen.getByRole('button', { name: /Save token/ }));

    expect(onTokenChange).toHaveBeenCalledWith('abc');
  });

  it('shows the reported state and backend error', () => {
    render(
      <CallStatePanel
        {...BASE_PROPS}
        activeCall
        observedAt="2026-10-20T09:00:00Z"
        error="The device pairing token was rejected."
      />,
    );

    expect(screen.getByText('Call in progress')).toBeInTheDocument();
    expect(screen.getByText('The device pairing token was rejected.')).toBeInTheDocument();
    expect(screen.getByText(/Reported at/)).toBeInTheDocument();
  });
});
