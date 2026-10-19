import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import { useMemo } from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef } from 'ag-grid-community';

interface TransactionRow {
  id: string;
  date: string;
  merchant: string;
  amount: string;
  riskScore: number | null;
}

// AG Grid shell for the transaction/risk table (later milestone feeds it real
// rows from GET /api/transactions). No data fetching yet.
export default function TransactionTable() {
  const columnDefs = useMemo<ColDef<TransactionRow>[]>(
    () => [
      { field: 'id', headerName: 'Transaction ID' },
      { field: 'date', headerName: 'Date' },
      { field: 'merchant', headerName: 'Merchant' },
      { field: 'amount', headerName: 'Amount' },
      { field: 'riskScore', headerName: 'Risk Score' },
    ],
    [],
  );

  return (
    <div className="ag-theme-quartz" style={{ height: 320, width: '100%' }}>
      <AgGridReact<TransactionRow>
        rowData={[]}
        columnDefs={columnDefs}
        pagination
        paginationPageSize={20}
      />
    </div>
  );
}
