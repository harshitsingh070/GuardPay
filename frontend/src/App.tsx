import type { ReactNode } from 'react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Loader2, ShieldAlert } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import RootLayout from './layouts/RootLayout.tsx';
import AdminFeedbackPage from './pages/AdminFeedbackPage.tsx';
import DashboardPage from './pages/DashboardPage.tsx';
import LearningAdminPage from './pages/LearningAdminPage.tsx';
import GuardedPaymentPage from './pages/GuardedPaymentPage.tsx';
import HomePage from './pages/HomePage.tsx';
import LoginPage from './pages/LoginPage.tsx';
import NotFoundPage from './pages/NotFoundPage.tsx';
import TransactionsPage from './pages/TransactionsPage.tsx';

function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#f8fafc] text-guardpay-muted">
        <Loader2 className="h-6 w-6 animate-spin" aria-label="Loading" />
      </div>
    );
  }
  if (status === 'anonymous') {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    );
  }
  return children;
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { user } = useAuth();

  if (user?.admin !== true) {
    return (
      <section className="mx-auto max-w-lg py-16 text-center">
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-amber-50 text-amber-700">
          <ShieldAlert className="h-6 w-6" aria-hidden="true" />
        </span>
        <h1 className="mt-4 text-2xl font-bold tracking-tight text-guardpay-ink">
          Admin access required
        </h1>
        <p className="mt-2 text-sm text-guardpay-muted">
          This console is limited to accounts promoted through server-side configuration. Ask an
          operator to add your email to <code>GUARDPAY_INTELLIGENCE_ADMIN_EMAILS</code>.
        </p>
        <Link
          to="/dashboard"
          className="mt-6 inline-flex rounded-full bg-guardpay-blue px-5 py-2.5 text-sm font-semibold text-white hover:bg-[#005ea6]"
        >
          Back to dashboard
        </Link>
      </section>
    );
  }
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <RootLayout />
          </RequireAuth>
        }
      >
        <Route index element={<HomePage />} />
        <Route path="payment" element={<GuardedPaymentPage />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="transactions" element={<TransactionsPage />} />
        <Route
          path="admin/learning"
          element={
            <RequireAdmin>
              <LearningAdminPage />
            </RequireAdmin>
          }
        />
        <Route
          path="admin/feedback"
          element={
            <RequireAdmin>
              <AdminFeedbackPage />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
