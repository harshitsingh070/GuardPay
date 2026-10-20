import { Link } from 'react-router-dom';
import { ArrowLeft, Compass } from 'lucide-react';
import { usePageTitle } from '@/hooks/usePageTitle';

export default function NotFoundPage() {
  usePageTitle('Not found');
  return (
    <section className="mx-auto flex max-w-xl flex-col items-center py-12 text-center sm:py-20">
      <span className="grid h-16 w-16 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
        <Compass className="h-8 w-8" aria-hidden="true" />
      </span>
      <p className="mt-5 text-xs font-bold uppercase tracking-[0.2em] text-guardpay-muted">404</p>
      <h1 className="mt-1 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
        Page not found
      </h1>
      <p className="mt-2 max-w-md text-sm leading-relaxed text-guardpay-muted">
        This route does not exist yet. Head back to the home page or start a protected payment.
      </p>
      <div className="mt-6 flex flex-col gap-2 sm:flex-row">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center justify-center gap-2 rounded-full bg-guardpay-blue px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#005ea6]"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Go home
        </Link>
        <Link
          to="/payment"
          className="inline-flex min-h-11 items-center justify-center rounded-full border border-guardpay-line bg-white px-5 py-2.5 text-sm font-semibold text-guardpay-ink transition-colors hover:bg-guardpay-canvas"
        >
          Make a payment
        </Link>
      </div>
    </section>
  );
}
