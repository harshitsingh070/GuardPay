import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { ArrowRight, LogOut, Menu, ShieldCheck, X } from 'lucide-react';
import AlertBanner from '@/components/alerts/AlertBanner';
import { AlertsProvider, useAlerts } from '@/hooks/useAlerts';
import { useAuth } from '@/hooks/useAuth';
import { cn } from '@/utils/cn';

const links: { to: string; label: string; admin?: boolean }[] = [
  { to: '/payment', label: 'Payment' },
  { to: '/transactions', label: 'Transactions' },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/admin/feedback', label: 'Review', admin: true },
  { to: '/admin/learning', label: 'Learning', admin: true },
];

function desktopLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'rounded-full px-3.5 py-2 text-sm transition-colors',
    isActive
      ? 'bg-[#eaf2ff] font-semibold text-guardpay-navy'
      : 'text-guardpay-muted hover:bg-guardpay-canvas hover:text-guardpay-ink',
  );
}

function mobileLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'flex items-center justify-between rounded-xl px-3.5 py-3 text-sm font-medium transition-colors',
    isActive
      ? 'bg-[#eaf2ff] text-guardpay-navy'
      : 'text-guardpay-muted hover:bg-guardpay-canvas hover:text-guardpay-ink',
  );
}

function RootLayoutContent() {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const { latest, clearLatest } = useAlerts();
  const { user, logout } = useAuth();
  const visibleLinks = links.filter((link) => link.admin !== true || user?.admin === true);

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  return (
    <div className="flex min-h-screen flex-col bg-[#f8fafc] text-foreground">
      <header className="sticky top-0 z-40 border-b border-guardpay-line/80 bg-white/90 backdrop-blur">
        <nav
          className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6"
          aria-label="Primary"
        >
          <Link
            to="/"
            className="flex shrink-0 items-center gap-2.5 rounded-xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue"
          >
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-guardpay-blue to-guardpay-sky text-white shadow-sm shadow-guardpay-blue/25">
              <ShieldCheck className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className="flex flex-col leading-none">
              <span className="text-base font-bold tracking-tight text-guardpay-ink">GuardPay</span>
              <span className="mt-1 hidden text-[10px] font-medium uppercase tracking-[0.18em] text-guardpay-muted sm:block">
                Payment safety
              </span>
            </span>
          </Link>

          <div className="hidden items-center gap-1 lg:flex">
            {visibleLinks.map((link) => (
              <NavLink key={link.to} to={link.to} end className={desktopLinkClass}>
                {link.label}
              </NavLink>
            ))}
          </div>

          <div className="flex items-center gap-2">
            {user !== null && (
              <span className="hidden max-w-32 truncate text-xs font-medium text-guardpay-muted md:block">
                {user.displayName}
              </span>
            )}
            <Link
              to="/payment"
              className="hidden items-center gap-1.5 rounded-full bg-guardpay-blue px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#005ea6] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue sm:inline-flex"
            >
              Pay securely
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <button
              type="button"
              onClick={() => void logout()}
              className="hidden h-10 w-10 items-center justify-center rounded-xl border border-guardpay-line bg-white text-guardpay-muted transition-colors hover:bg-guardpay-canvas hover:text-guardpay-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue sm:inline-flex"
              aria-label="Sign out"
              title="Sign out"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
            </button>
            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-guardpay-line bg-white text-guardpay-muted transition-colors hover:bg-guardpay-canvas hover:text-guardpay-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-guardpay-blue lg:hidden"
              aria-expanded={menuOpen}
              aria-controls="mobile-nav"
              aria-label={menuOpen ? 'Close navigation' : 'Open navigation'}
            >
              {menuOpen ? (
                <X className="h-5 w-5" aria-hidden="true" />
              ) : (
                <Menu className="h-5 w-5" aria-hidden="true" />
              )}
            </button>
          </div>
        </nav>

        {menuOpen && (
          <nav
            id="mobile-nav"
            aria-label="Mobile"
            className="mx-auto w-full max-w-6xl border-t border-guardpay-line px-3 pb-4 pt-3 lg:hidden"
          >
            <div className="space-y-1">
              {visibleLinks.map((link) => (
                <NavLink key={link.to} to={link.to} end className={mobileLinkClass}>
                  {link.label}
                  <ArrowRight className="h-4 w-4 opacity-40" aria-hidden="true" />
                </NavLink>
              ))}
            </div>
            <Link
              to="/payment"
              className="mt-3 flex items-center justify-center gap-1.5 rounded-full bg-guardpay-blue px-4 py-3 text-sm font-semibold text-white shadow-sm hover:bg-[#005ea6]"
            >
              Make a protected payment
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <div className="mt-3 flex items-center justify-between rounded-xl border border-guardpay-line px-3.5 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-guardpay-ink">
                  {user?.displayName ?? 'Signed in'}
                </p>
                <p className="truncate text-xs text-guardpay-muted">{user?.email}</p>
              </div>
              <button
                type="button"
                onClick={() => void logout()}
                className="inline-flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold text-guardpay-muted hover:bg-guardpay-canvas hover:text-guardpay-ink"
              >
                <LogOut className="h-3.5 w-3.5" aria-hidden="true" />
                Sign out
              </button>
            </div>
          </nav>
        )}
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 sm:py-10">
        <Outlet />
      </main>

      <footer className="border-t border-guardpay-line bg-white">
        <div className="mx-auto grid w-full max-w-6xl gap-8 px-4 py-8 sm:px-6 md:grid-cols-[1.5fr_1fr_1fr]">
          <div>
            <Link to="/" className="inline-flex items-center gap-2.5">
              <span className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-guardpay-blue to-guardpay-sky text-white">
                <ShieldCheck className="h-4 w-4" aria-hidden="true" />
              </span>
              <span className="text-sm font-bold text-guardpay-ink">GuardPay</span>
            </Link>
            <p className="mt-3 max-w-sm text-xs leading-relaxed text-guardpay-muted">
              An explainable payment-safety layer for PayPal Sandbox. GuardPay checks the context
              around a payment before an order is created — and shows its reasoning.
            </p>
          </div>

          <div>
            <h2 className="text-xs font-bold uppercase tracking-wider text-guardpay-ink">Product</h2>
            <ul className="mt-3 space-y-2 text-xs text-guardpay-muted">
              <li>
                <Link to="/payment" className="hover:text-guardpay-blue">
                  Protected payment
                </Link>
              </li>
              <li>
                <Link to="/transactions" className="hover:text-guardpay-blue">
                  Transaction history
                </Link>
              </li>
              <li>
                <Link to="/dashboard" className="hover:text-guardpay-blue">
                  Security dashboard
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <h2 className="text-xs font-bold uppercase tracking-wider text-guardpay-ink">Account</h2>
            <ul className="mt-3 space-y-2 text-xs text-guardpay-muted">
              <li>Private per-user workspace</li>
              <li>Session-cookie authentication</li>
              <li>PayPal Sandbox only</li>
            </ul>
          </div>
        </div>

        <div className="border-t border-guardpay-line">
          <div className="mx-auto flex w-full max-w-6xl flex-col gap-1 px-4 py-4 text-xs text-guardpay-muted sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <p>© {new Date().getFullYear()} GuardPay · Hackathon prototype</p>
            <p>Not affiliated with PayPal. No real money moves.</p>
          </div>
        </div>
      </footer>

      <AlertBanner alert={latest} onDismiss={clearLatest} />
    </div>
  );
}

export default function RootLayout() {
  return (
    <AlertsProvider>
      <RootLayoutContent />
    </AlertsProvider>
  );
}
