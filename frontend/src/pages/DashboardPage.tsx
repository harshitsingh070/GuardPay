import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Gauge,
  LayoutDashboard,
  ListChecks,
  ShieldCheck,
  Sparkles,
  Wallet,
} from 'lucide-react';
import { usePageTitle } from '@/hooks/usePageTitle';

const METRICS = [
  { Icon: ShieldCheck, label: 'Decisions reviewed', hint: 'Every stored payment decision' },
  { Icon: Gauge, label: 'Average risk score', hint: 'Across allowed and blocked payments' },
  { Icon: Wallet, label: 'Sandbox volume', hint: 'Captured PayPal Sandbox totals' },
];

const ACTIONS = [
  {
    to: '/payment',
    Icon: ShieldCheck,
    title: 'Make a protected payment',
    body: 'Run the live GuardPay checkout against PayPal Sandbox.',
  },
  {
    to: '/transactions',
    Icon: ListChecks,
    title: 'Review transaction history',
    body: 'Inspect stored decisions, signals, and PayPal references.',
  },
  {
    to: '/payment/manual',
    Icon: Sparkles,
    title: 'Try the manual risk demo',
    body: 'Toggle individual signals and evaluate a payment on demand.',
  },
];

const PIPELINE = [
  'Capture the payment context — amount, recipient, message, link, and call state.',
  'Apply deterministic rules and compare the payment with stored history.',
  'Attach AI evidence when available, without letting it override the rules.',
  'Store the final ALLOW, VERIFY, or STOP decision for auditing.',
];

export default function DashboardPage() {
  usePageTitle('Dashboard');

  return (
    <section>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
            <LayoutDashboard className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="gp-eyebrow">Overview</p>
            <h1 className="mt-0.5 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
              Dashboard
            </h1>
            <p className="mt-1 max-w-xl text-sm text-guardpay-muted">
              A security overview of GuardPay decisions, signals, and Sandbox activity.
            </p>
          </div>
        </div>
        <span className="inline-flex items-center gap-1.5 rounded-full border border-[#cfe4ff] bg-[#eaf2ff] px-3 py-1 text-xs font-semibold text-guardpay-navy">
          <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
          Analytics milestone
        </span>
      </div>

      <div className="mt-6 grid gap-3 sm:grid-cols-3">
        {METRICS.map(({ Icon, label, hint }) => (
          <div key={label} className="gp-card p-5">
            <div className="flex items-center justify-between gap-2">
              <span className="grid h-9 w-9 place-items-center rounded-xl bg-guardpay-canvas text-guardpay-muted">
                <Icon className="h-4 w-4" aria-hidden="true" />
              </span>
              <span className="rounded-full bg-guardpay-canvas px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                Planned
              </span>
            </div>
            <p className="mt-3 text-2xl font-bold tabular-nums text-guardpay-muted">—</p>
            <p className="mt-1 text-sm font-semibold text-guardpay-ink">{label}</p>
            <p className="mt-0.5 text-xs text-guardpay-muted">{hint}</p>
          </div>
        ))}
      </div>

      <p className="mt-3 text-xs text-guardpay-muted">
        Live aggregates arrive in a later milestone. Transaction-level decisions are already
        available in history.
      </p>

      <div className="mt-6 grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="gp-card p-5 sm:p-6">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-guardpay-ink">Quick actions</h2>
          </div>
          <p className="mt-1 text-sm text-guardpay-muted">
            Jump straight into the parts of GuardPay that are live today.
          </p>

          <div className="mt-4 space-y-2.5">
            {ACTIONS.map(({ to, Icon, title, body }) => (
              <Link
                key={to}
                to={to}
                className="group flex items-start gap-3 rounded-xl border border-guardpay-line bg-white p-3.5 transition-colors hover:border-guardpay-blue/30 hover:bg-[#f9fbff]"
              >
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[#eaf2ff] text-guardpay-blue">
                  <Icon className="h-4 w-4" aria-hidden="true" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold text-guardpay-ink">{title}</span>
                  <span className="mt-0.5 block text-xs text-guardpay-muted">{body}</span>
                </span>
                <ArrowRight
                  className="mt-2 h-4 w-4 shrink-0 text-guardpay-muted transition-transform group-hover:translate-x-0.5 group-hover:text-guardpay-blue"
                  aria-hidden="true"
                />
              </Link>
            ))}
          </div>
        </div>

        <div className="gp-card p-5 sm:p-6">
          <h2 className="text-lg font-bold text-guardpay-ink">How a decision is made</h2>
          <p className="mt-1 text-sm text-guardpay-muted">
            The same server-side pipeline powers every checkout and manual evaluation.
          </p>
          <ol className="mt-4 space-y-3">
            {PIPELINE.map((step, index) => (
              <li key={step} className="flex gap-3">
                <span
                  className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-[#eaf2ff] text-xs font-bold text-guardpay-navy"
                  aria-hidden="true"
                >
                  {index + 1}
                </span>
                <span className="pt-0.5 text-sm leading-relaxed text-guardpay-muted">{step}</span>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
}
