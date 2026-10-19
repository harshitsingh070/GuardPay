import { Link, NavLink, Outlet } from 'react-router-dom';
import { cn } from '@/utils/cn';

const links = [
  { to: '/', label: 'Home' },
  { to: '/payment', label: 'Payment' },
  { to: '/payment/manual', label: 'Manual demo' },
  { to: '/risk', label: 'Risk warning' },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/transactions', label: 'Transactions' },
];

export default function RootLayout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <nav className="mx-auto flex max-w-5xl items-center gap-6 px-4 py-3">
          <Link to="/" className="font-semibold">
            GuardPay
          </Link>
          <div className="flex items-center gap-4">
            {links.slice(1).map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) =>
                  cn(
                    'text-sm text-muted-foreground hover:text-foreground',
                    isActive && 'font-medium text-foreground',
                  )
                }
              >
                {link.label}
              </NavLink>
            ))}
          </div>
        </nav>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
