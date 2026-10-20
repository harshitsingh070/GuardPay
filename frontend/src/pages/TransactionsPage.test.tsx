import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TransactionsPage from '@/pages/TransactionsPage';
import { ApiRequestError } from '@/services/api-client';
import type { TransactionHistoryService } from '@/types/transaction';
import { deferred, transactionPage } from '@/test/fixtures';

function setup() {
  const list = vi.fn<TransactionHistoryService['list']>();
  const detail = vi.fn<TransactionHistoryService['detail']>();
  const service: TransactionHistoryService = { list, detail };
  return { list, detail, service };
}

function renderPage(service: TransactionHistoryService) {
  return render(
    <MemoryRouter>
      <TransactionsPage service={service} />
    </MemoryRouter>,
  );
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows a loading state while the history loads', () => {
    const { list, service } = setup();
    list.mockReturnValue(deferred<never>().promise as never);
    renderPage(service);
    expect(screen.getByRole('status')).toHaveTextContent('Loading transactions');
  });

  it('invites the user to make a payment when history is empty', async () => {
    const { list, service } = setup();
    list.mockResolvedValue(transactionPage({ items: [], totalItems: 0, totalPages: 0 }));
    renderPage(service);

    expect(await screen.findByText(/No transactions yet/)).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith(undefined, 0, 20);
  });

  it('surfaces a load failure', async () => {
    const { list, service } = setup();
    list.mockRejectedValue(new ApiRequestError(0, 'Unable to reach GuardPay.'));
    renderPage(service);

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to reach GuardPay.');
  });

  it('renders the grid for a non-empty history', async () => {
    const { list, service } = setup();
    list.mockResolvedValue(transactionPage());
    renderPage(service);

    expect(await screen.findByTestId('transaction-grid')).toBeInTheDocument();
    await waitFor(() => expect(list).toHaveBeenCalledTimes(1));
  });
});
