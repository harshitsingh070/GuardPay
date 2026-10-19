import { Link } from 'react-router-dom';
import { usePageTitle } from '@/hooks/usePageTitle';

// GuardPay home (scaffold — links to planned areas only).
export default function HomePage() {
  usePageTitle('Home');
  return (
    <section>
      <h1 className="text-2xl font-semibold">GuardPay</h1>
      <p className="mt-2 text-muted-foreground">
        Scaffold — payment flow, risk warning, and dashboard arrive in later milestones.
      </p>
      <div className="mt-4 flex gap-4">
        <Link to="/payment" className="underline">
          Analyze a payment
        </Link>
        <Link to="/dashboard" className="underline">
          Open dashboard
        </Link>
      </div>
    </section>
  );
}
