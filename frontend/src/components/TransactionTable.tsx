import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import { useMemo } from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, RowClickedEvent } from 'ag-grid-community';
import type { TransactionSummary } from '@/types/transaction';

interface Props {
  rows?: TransactionSummary[];
  onSelect?: (id: string) => void;
}

interface Row {
  id: string;
  date: string;
  recipient: string;
  merchant: string;
  amount: string;
  status: string;
  risk: string;
}

function money(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
}

function toRow(row: TransactionSummary): Row {
  return {
    id: row.id,
    date: new Date(row.transactionTimestamp).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }),
    recipient: row.recipientName ?? '—',
    merchant: row.merchantName ?? '—',
    amount: money(row.amount, row.currency),
    status: row.status,
    risk:
      row.riskLevel === null
        ? '—'
        : `${row.recommendedAction} · ${row.riskLevel} (${row.riskScore}/100)`,
  };
}

function riskChipClass(action: string | null, level: string | null): string {
  if (action === 'STOP' || level === 'CRITICAL') return 'bg-red-100 text-red-800';
  if (action === 'VERIFY' || level === 'HIGH' || level === 'MEDIUM') {
    return 'bg-amber-100 text-amber-800';
  }
  if (action === 'ALLOW' || level === 'LOW') return 'bg-emerald-100 text-emerald-800';
  return 'bg-guardpay-canvas text-guardpay-muted';
}

function statusChipClass(status: string): string {
  if (status === 'COMPLETED' || status === 'CAPTURED') return 'bg-emerald-100 text-emerald-800';
  if (status === 'FAILED' || status === 'BLOCKED' || status === 'DECLINED') {
    return 'bg-red-100 text-red-800';
  }
  return 'bg-guardpay-canvas text-guardpay-muted';
}

/**
 * AG Grid history table. Rows come straight from the backend — sorting,
 * filtering and paging are the grid's job; no decision is computed here.
 */
export default function TransactionTable({ rows = [], onSelect }: Props) {
  const columnDefs = useMemo<ColDef<Row>[]>(
    () => [
      { field: 'date', headerName: 'Date', flex: 1.1, minWidth: 120 },
      { field: 'recipient', headerName: 'Recipient', flex: 1, minWidth: 92 },
      { field: 'merchant', headerName: 'Merchant', flex: 1, minWidth: 92 },
      { field: 'amount', headerName: 'Amount', flex: 0.8, minWidth: 82 },
      { field: 'status', headerName: 'Status', flex: 0.9, minWidth: 100 },
      {
        field: 'risk',
        headerName: 'GuardPay decision',
        flex: 1.7,
        minWidth: 175,
        cellStyle: { fontSize: '12px', whiteSpace: 'nowrap' },
      },
    ],
    [],
  );

  const data = useMemo(() => rows.map(toRow), [rows]);

  function handleRowClicked(event: RowClickedEvent<Row>) {
    if (event.data !== undefined && onSelect !== undefined) onSelect(event.data.id);
  }

  return (
    <>
      <ul className="space-y-2 sm:hidden" data-testid="transaction-cards">
        {rows.map((row) => {
          const date = new Date(row.transactionTimestamp);
          const dateLabel = Number.isNaN(date.getTime())
            ? row.transactionTimestamp
            : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
          return (
            <li key={row.id}>
              <button
                type="button"
                onClick={() => onSelect?.(row.id)}
                className="w-full rounded-xl border border-guardpay-line bg-white p-4 text-left shadow-sm transition-colors hover:border-guardpay-blue/30 hover:bg-[#f9fbff] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue"
                aria-label={`View transaction for ${row.recipientName ?? 'new recipient'}`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-guardpay-ink">
                      {row.recipientName ?? 'New recipient'}
                    </p>
                    <p className="mt-0.5 truncate text-xs text-guardpay-muted">
                      {row.merchantName ?? 'No merchant'} · {dateLabel}
                    </p>
                  </div>
                  <p className="shrink-0 text-sm font-bold tabular-nums text-guardpay-ink">
                    {money(row.amount, row.currency)}
                  </p>
                </div>
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-bold tracking-wide ${statusChipClass(row.status)}`}
                  >
                    {row.status}
                  </span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-bold tracking-wide ${riskChipClass(row.recommendedAction, row.riskLevel)}`}
                  >
                    {row.riskLevel === null
                      ? 'No GuardPay decision'
                      : `${row.recommendedAction} · ${row.riskLevel} (${row.riskScore}/100)`}
                  </span>
                </div>
              </button>
            </li>
          );
        })}
      </ul>

      <div className="hidden sm:block">
        <div
          className="ag-theme-quartz guardpay-grid"
          style={{ height: 400, width: '100%' }}
          data-testid="transaction-grid"
        >
          <AgGridReact<Row>
            rowData={data}
            columnDefs={columnDefs}
            getRowId={(params) => params.data.id}
            onRowClicked={handleRowClicked}
            pagination
            paginationPageSize={20}
          />
        </div>
      </div>
    </>
  );
}
