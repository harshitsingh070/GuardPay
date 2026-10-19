import TransactionTable from '@/components/TransactionTable';
import { usePageTitle } from '@/hooks/usePageTitle';

// Planned: security dashboard with AG Grid summary + transaction table.
export default function DashboardPage() {
  usePageTitle('Dashboard');
  return (
    <section>
      <h1 className="text-2xl font-semibold">Dashboard</h1>
      <p className="mt-2 text-muted-foreground">
        Planned security dashboard incl. AG Grid transaction table (later milestone).
      </p>
      <div className="mt-4">
        <TransactionTable />
      </div>
    </section>
  );
}
