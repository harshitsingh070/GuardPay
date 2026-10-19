import TransactionTable from '@/components/TransactionTable';
import { usePageTitle } from '@/hooks/usePageTitle';

// Planned: transaction history (AG Grid — sorting/filtering/pagination/detail).
export default function TransactionsPage() {
  usePageTitle('Transactions');
  return (
    <section>
      <h1 className="text-2xl font-semibold">Transactions</h1>
      <p className="mt-2 text-muted-foreground">
        Planned AG Grid transaction history (later milestone).
      </p>
      <div className="mt-4">
        <TransactionTable />
      </div>
    </section>
  );
}
