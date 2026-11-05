import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import TransactionDetailPanel from '@/components/TransactionDetailPanel';
import { transactionDetail } from '@/test/fixtures';

describe('TransactionDetailPanel', () => {
  it('prompts for a selection when nothing is chosen', () => {
    render(<TransactionDetailPanel detail={null} loading={false} error={null} />);
    expect(
      screen.getByText('Select a transaction to see what GuardPay decided and why.'),
    ).toBeInTheDocument();
  });

  it('shows the stored decision, findings and PayPal references', () => {
    render(
      <TransactionDetailPanel detail={transactionDetail()} loading={false} error={null} />,
    );

    expect(screen.getByText('$45.00')).toBeInTheDocument();
    expect(screen.getByText('VERIFY')).toBeInTheDocument();
    expect(screen.getByText('Likely fraud type')).toBeInTheDocument();
    expect(screen.getByText('Unverified new-recipient payment')).toBeInTheDocument();
    expect(screen.getByText('New recipient')).toBeInTheDocument();
    expect(screen.getByText('ORDER-1')).toBeInTheDocument();
    expect(screen.getByText('CAPTURE-1')).toBeInTheDocument();
    expect(screen.getByText('VERIFIED')).toBeInTheDocument();
  });

  it('surfaces a loading state', () => {
    render(<TransactionDetailPanel detail={null} loading error={null} />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading detail');
  });

  it('surfaces an error state', () => {
    render(
      <TransactionDetailPanel detail={null} loading={false} error="Unknown transaction" />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Unknown transaction');
  });
});
