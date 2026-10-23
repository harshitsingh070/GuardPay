import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Check,
  CheckCircle2,
  FlaskConical,
  ListChecks,
  Lock,
  OctagonX,
  PhoneCall,
  ShieldCheck,
  TriangleAlert,
  Wallet,
} from 'lucide-react';
import { usePageTitle } from '@/hooks/usePageTitle';

const STEPS = [
  {
    title: 'Share the payment context',
    body: 'Recipient, merchant, amount, and an optional message or link.',
  },
  {
    title: 'GuardPay checks it',
    body: 'Your history, known merchants, scam language, links, and call state.',
  },
  {
    title: 'Get a clear decision',
    body: 'ALLOW, VERIFY with a one-time code, or STOP before any PayPal order exists.',
  },
];

const FEATURES = [
  {
    Icon: PhoneCall,
    title: 'Call-aware checks',
    body: 'Pair an Android device to add an active-phone-call signal. Phone browsers use a manual demo toggle.',
  },
  {
    Icon: ListChecks,
    title: 'Explainable decisions',
    body: 'Every result shows the signals and weights behind it, so nothing feels like a black box.',
  },
  {
    Icon: Lock,
    title: 'Sandbox by design',
    body: 'PayPal Sandbox only. GuardPay creates no real charges and never inspects your PayPal account.',
  },
];

const EXAMPLES = [
  {
    Icon: CheckCircle2,
    title: 'Known merchant',
    detail: 'Recurring software invoice',
    label: 'ALLOW',
    iconClass: 'text-emerald-600',
    boxClass: 'border-emerald-200 bg-emerald-50',
    chipClass: 'bg-emerald-100 text-emerald-800',
  },
  {
    Icon: TriangleAlert,
    title: 'New recipient',
    detail: '$2,499 · first-time payee',
    label: 'VERIFY',
    iconClass: 'text-amber-600',
    boxClass: 'border-amber-200 bg-amber-50',
    chipClass: 'bg-amber-100 text-amber-800',
  },
  {
    Icon: OctagonX,
    title: 'Urgent payment on a call',
    detail: 'Pressure to pay + suspicious link',
    label: 'STOP',
    iconClass: 'text-red-600',
    boxClass: 'border-red-200 bg-red-50',
    chipClass: 'bg-red-100 text-red-800',
  },
];

const STATS = [
  { value: '3', label: 'clear outcomes', detail: 'ALLOW · VERIFY · STOP' },
  { value: '0–100', label: 'explainable score', detail: 'Every signal has a weight' },
  { value: 'Call-aware', label: 'payment context', detail: 'Android detector or manual toggle' },
];

export default function HomePage() {
  usePageTitle('Home');

  return (
    <section className="space-y-12 sm:space-y-16">
      <div className="grid gap-10 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)] lg:items-center">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-[#cfe4ff] bg-[#eaf2ff] px-3 py-1 text-xs font-semibold text-guardpay-navy">
            <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
            Payment safety for PayPal Sandbox
          </span>
          <h1 className="mt-4 text-4xl font-bold leading-[1.05] tracking-tight text-guardpay-ink sm:text-5xl lg:text-6xl">
            Know before
            <br />
            <span className="bg-gradient-to-r from-guardpay-blue to-guardpay-sky bg-clip-text text-transparent">
              you pay.
            </span>
          </h1>
          <p className="mt-4 max-w-xl text-base leading-relaxed text-guardpay-muted sm:text-lg">
            GuardPay reviews who you are paying, why, and whether you are on a phone call before a
            PayPal order is created — and explains every decision in plain language.
          </p>

          <div className="mt-6 flex flex-col gap-3 sm:flex-row">
            <Link
              to="/payment"
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-full bg-guardpay-blue px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#005ea6] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue"
            >
              Make a protected payment
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <Link
              to="/transactions"
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-full border border-guardpay-line bg-white px-6 py-3 text-sm font-semibold text-guardpay-ink transition-colors hover:border-guardpay-blue/40 hover:bg-guardpay-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue"
            >
              View transactions
            </Link>
          </div>

          <div className="mt-5 flex flex-wrap gap-x-4 gap-y-2 text-xs text-guardpay-muted">
            <span className="inline-flex items-center gap-1.5">
              <Check className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
              Sandbox only
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Check className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
              No real money moves
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Check className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
              Server-side decisions
            </span>
          </div>
        </div>

        <div className="relative">
          <div
            className="absolute -inset-3 -z-10 rounded-[2rem] bg-gradient-to-br from-[#eaf2ff] via-white to-[#e6f7ff] blur-2xl"
            aria-hidden="true"
          />
          <div className="gp-card p-5 sm:p-6">
            <div className="flex items-center justify-between gap-3">
              <p className="gp-eyebrow">Example decisions</p>
              <span className="rounded-full bg-guardpay-canvas px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                Illustrative
              </span>
            </div>

            <div className="mt-4 space-y-3">
              {EXAMPLES.map(({ Icon, title, detail, label, iconClass, boxClass, chipClass }) => (
                <div
                  key={title}
                  className={`flex items-center gap-3 rounded-xl border p-3.5 ${boxClass}`}
                >
                  <Icon className={`h-5 w-5 shrink-0 ${iconClass}`} aria-hidden="true" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-guardpay-ink">{title}</p>
                    <p className="truncate text-xs text-guardpay-muted">{detail}</p>
                  </div>
                  <span
                    className={`shrink-0 rounded-full px-2.5 py-1 text-[10px] font-bold tracking-wide ${chipClass}`}
                  >
                    {label}
                  </span>
                </div>
              ))}
            </div>

            <p className="mt-4 flex items-start gap-2 border-t border-guardpay-line pt-4 text-xs text-guardpay-muted">
              <ListChecks className="mt-0.5 h-3.5 w-3.5 shrink-0 text-guardpay-blue" aria-hidden="true" />
              Every result ships with the signals and weights behind it.
            </p>
          </div>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        {STATS.map((stat) => (
          <div key={stat.label} className="gp-card px-5 py-4">
            <p className="text-xl font-bold tracking-tight text-guardpay-ink sm:text-2xl">
              {stat.value}
            </p>
            <p className="mt-0.5 text-sm font-semibold text-guardpay-ink">{stat.label}</p>
            <p className="mt-0.5 text-xs text-guardpay-muted">{stat.detail}</p>
          </div>
        ))}
      </div>

      <div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="gp-eyebrow">How it works</p>
            <h2 className="mt-1 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
              Three steps before checkout
            </h2>
          </div>
          <p className="max-w-md text-sm text-guardpay-muted">
            GuardPay never creates a PayPal order until the decision allows it.
          </p>
        </div>

        <ol className="mt-5 grid gap-4 md:grid-cols-3">
          {STEPS.map((step, index) => (
            <li key={step.title} className="gp-card relative p-5">
              <span
                className="grid h-9 w-9 place-items-center rounded-full bg-[#eaf2ff] text-sm font-bold text-guardpay-navy"
                aria-hidden="true"
              >
                {index + 1}
              </span>
              <h3 className="mt-3 text-base font-semibold text-guardpay-ink">{step.title}</h3>
              <p className="mt-1 text-sm leading-relaxed text-guardpay-muted">{step.body}</p>
            </li>
          ))}
        </ol>
      </div>

      <div>
        <p className="gp-eyebrow">Why GuardPay</p>
        <h2 className="mt-1 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
          Safety you can see through
        </h2>
        <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(({ Icon, title, body }) => (
            <article
              key={title}
              className="gp-card group p-5 transition-all hover:-translate-y-0.5 hover:border-guardpay-blue/30 hover:shadow-md"
            >
              <span className="grid h-10 w-10 place-items-center rounded-xl bg-[#eaf2ff] text-guardpay-blue transition-colors group-hover:bg-guardpay-blue group-hover:text-white">
                <Icon className="h-5 w-5" aria-hidden="true" />
              </span>
              <h3 className="mt-3 text-base font-semibold text-guardpay-ink">{title}</h3>
              <p className="mt-1 text-sm leading-relaxed text-guardpay-muted">{body}</p>
            </article>
          ))}
        </div>
      </div>

      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-guardpay-ink via-guardpay-navy to-guardpay-blue px-6 py-10 text-white sm:px-10 sm:py-12">
        <div
          className="pointer-events-none absolute right-6 top-2 h-56 w-56 rounded-full bg-white/10 blur-2xl"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -bottom-24 left-1/3 h-56 w-56 rounded-full bg-guardpay-sky/20 blur-3xl"
          aria-hidden="true"
        />
        <div className="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-white/90">
              <FlaskConical className="h-3.5 w-3.5" aria-hidden="true" />
              Hackathon prototype
            </p>
            <h2 className="mt-3 text-2xl font-bold tracking-tight sm:text-3xl">
              Try a payment that explains itself.
            </h2>
            <p className="mt-2 max-w-xl text-sm text-white/70">
              Run a Sandbox payment, see the signals, and approve it in PayPal. No real money moves.
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link
              to="/payment"
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-full bg-white px-6 py-3 text-sm font-semibold text-guardpay-navy transition-colors hover:bg-[#eaf2ff]"
            >
              <Wallet className="h-4 w-4" aria-hidden="true" />
              Start a payment
            </Link>
            <Link
              to="/transactions"
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-full border border-white/25 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-white/10"
            >
              View transaction history
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
