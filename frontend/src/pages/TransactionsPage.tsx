import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  History,
  Inbox,
  Loader2,
  RefreshCw,
} from 'lucide-react';
import TransactionDetailPanel from '@/components/TransactionDetailPanel';
import TransactionTable from '@/components/TransactionTable';
import { usePageTitle } from '@/hooks/usePageTitle';
import { ApiRequestError } from '@/services/api-client';
import { getTransactionHistoryService } from '@/services/transaction-service';
import type {
  TransactionDetail,
  TransactionHistoryService,
  TransactionPage,
} from '@/types/transaction';

const PAGE_SIZE = 20;

function messageOf(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message;
  return 'Unable to load transaction history. Please try again.';
}

/** Transaction history: the backend's stored decisions, browsable and explainable. */
export default function TransactionsPage({
  service = getTransactionHistoryService(),
}: {
  service?: TransactionHistoryService;
}) {
  usePageTitle('Transactions');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [data, setData] = useState<TransactionPage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [detail, setDetail] = useState<TransactionDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setListLoading(true);
    setListError(null);
    service
      .list(page, PAGE_SIZE)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((error: unknown) => {
        if (active) setListError(messageOf(error));
      })
      .finally(() => {
        if (active) setListLoading(false);
      });
    return () => {
      active = false;
    };
  }, [service, page, reload]);

  const selectRow = useCallback(
    (id: string) => {
      setDetail(null);
      setDetailError(null);
      setDetailLoading(true);
      service
        .detail(id)
        .then(setDetail)
        .catch((error: unknown) => setDetailError(messageOf(error)))
        .finally(() => setDetailLoading(false));
    },
    [service],
  );

  const items = data?.items ?? [];

  return (
    <section>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
            <History className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="gp-eyebrow">History</p>
            <h1 className="mt-0.5 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
              Transactions
            </h1>
            <p className="mt-1 max-w-xl text-sm text-guardpay-muted">
              Every payment GuardPay evaluated, with the decision that was stored server-side.
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => setReload((current) => current + 1)}
          className="inline-flex items-center gap-2 rounded-full border border-guardpay-line bg-white px-4 py-2.5 text-sm font-semibold text-guardpay-ink transition-colors hover:border-guardpay-blue/40 hover:bg-guardpay-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue"
        >
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
          Refresh
        </button>
      </div>

      {listLoading && (
        <div className="gp-card mt-6 flex items-center gap-3 p-6" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-guardpay-blue" aria-hidden="true" />
          <span className="text-sm text-guardpay-muted">Loading transactions…</span>
        </div>
      )}

      {!listLoading && listError !== null && (
        <div
          className="mt-6 rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-800 shadow-sm"
          role="alert"
        >
          <p className="font-semibold">Could not load transactions</p>
          <p className="mt-1">{listError}</p>
        </div>
      )}

      {!listLoading && listError === null && items.length === 0 && (
        <div className="gp-card mt-6 flex flex-col items-center justify-center px-6 py-14 text-center">
          <span className="grid h-14 w-14 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
            <Inbox className="h-7 w-7" aria-hidden="true" />
          </span>
          <h2 className="mt-4 text-lg font-bold text-guardpay-ink">No transactions yet.</h2>
          <p className="mt-1 max-w-md text-sm text-guardpay-muted">
            Payments you run through the checkout will appear here with their stored risk decision.
          </p>
          <Link
            to="/payment"
            className="mt-5 inline-flex min-h-11 items-center justify-center gap-2 rounded-full bg-guardpay-blue px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#005ea6]"
          >
            Make a protected payment
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
      )}

      {!listLoading && listError === null && items.length > 0 && data !== null && (
        <>
          <div className="gp-card mt-6 grid grid-cols-3 divide-x divide-guardpay-line">
            <div className="px-3 py-3.5 sm:px-5 sm:py-4">
              <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted sm:text-xs">
                Total recorded
              </p>
              <p className="mt-1 text-xl font-bold tabular-nums text-guardpay-ink sm:text-2xl">
                {data.totalItems}
              </p>
            </div>
            <div className="px-3 py-3.5 sm:px-5 sm:py-4">
              <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted sm:text-xs">
                This page
              </p>
              <p className="mt-1 text-xl font-bold tabular-nums text-guardpay-ink sm:text-2xl">
                {items.length}
              </p>
            </div>
            <div className="px-3 py-3.5 sm:px-5 sm:py-4">
              <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted sm:text-xs">
                Page
              </p>
              <p className="mt-1 text-xl font-bold tabular-nums text-guardpay-ink sm:text-2xl">
                {data.page + 1}
                <span className="text-sm font-medium text-guardpay-muted sm:text-base">
                  {' '}
                  / {Math.max(data.totalPages, 1)}
                </span>
              </p>
            </div>
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_23rem] lg:items-start">
            <div className="gp-card overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-guardpay-line px-4 py-3.5 sm:px-5">
                <div>
                  <h2 className="text-sm font-bold text-guardpay-ink">Payment history</h2>
                  <p className="text-xs text-guardpay-muted">
                    Select a row to inspect the stored decision.
                  </p>
                </div>
                <span className="hidden rounded-full bg-[#eaf2ff] px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-guardpay-navy sm:inline-flex">
                  Server decisions
                </span>
              </div>

              <div className="p-2 sm:p-3">
                <TransactionTable rows={items} onSelect={selectRow} />
              </div>

              {data.totalPages > 1 && (
                <div className="flex items-center justify-between gap-3 border-t border-guardpay-line px-4 py-3 text-sm sm:px-5">
                  <button
                    type="button"
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    disabled={page === 0}
                    className="inline-flex items-center gap-1 rounded-full border border-guardpay-line bg-white px-3 py-1.5 font-medium text-guardpay-ink transition-colors hover:bg-guardpay-canvas disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                    Previous
                  </button>
                  <span className="text-xs text-guardpay-muted">
                    Page {data.page + 1} of {data.totalPages}
                  </span>
                  <button
                    type="button"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={data.page + 1 >= data.totalPages}
                    className="inline-flex items-center gap-1 rounded-full border border-guardpay-line bg-white px-3 py-1.5 font-medium text-guardpay-ink transition-colors hover:bg-guardpay-canvas disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    Next
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </button>
                </div>
              )}
            </div>

            <TransactionDetailPanel detail={detail} loading={detailLoading} error={detailError} />
          </div>
        </>
      )}
    </section>
  );
}
