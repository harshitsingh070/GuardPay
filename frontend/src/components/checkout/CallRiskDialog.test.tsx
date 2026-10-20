import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import CallRiskDialog from '@/components/checkout/CallRiskDialog';

describe('CallRiskDialog', () => {
  it('renders the fraud warning with both choices when open', () => {
    render(<CallRiskDialog open onCancel={vi.fn()} onContinue={vi.fn()} />);

    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(screen.getByText('You are on a phone call')).toBeInTheDocument();
    expect(screen.getByText(/may be a fraudster/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel payment' })).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Continue anyway' })).toBeInTheDocument();
  });

  it('cancels or continues when the user chooses', async () => {
    const onCancel = vi.fn();
    const onContinue = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(
      <CallRiskDialog open onCancel={onCancel} onContinue={onContinue} />,
    );

    await user.click(screen.getByRole('button', { name: 'Continue anyway' }));
    expect(onContinue).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();

    rerender(<CallRiskDialog open onCancel={onCancel} onContinue={onContinue} />);
    await user.click(screen.getByRole('button', { name: 'Cancel payment' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('renders nothing when closed', () => {
    render(<CallRiskDialog open={false} onCancel={vi.fn()} onContinue={vi.fn()} />);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });
});
