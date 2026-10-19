import { Route, Routes } from 'react-router-dom';
import RootLayout from './layouts/RootLayout.tsx';
import DashboardPage from './pages/DashboardPage.tsx';
import GuardedPaymentPage from './pages/GuardedPaymentPage.tsx';
import HomePage from './pages/HomePage.tsx';
import NotFoundPage from './pages/NotFoundPage.tsx';
import PaymentPage from './pages/PaymentPage.tsx';
import RiskPage from './pages/RiskPage.tsx';
import TransactionsPage from './pages/TransactionsPage.tsx';

// GuardPay route map (React Router). /payment is the primary guarded flow;
// /payment/manual keeps the earlier manual-risk demo for regression.
export default function App() {
  return (
    <Routes>
      <Route element={<RootLayout />}>
        <Route index element={<HomePage />} />
        <Route path="payment" element={<GuardedPaymentPage />} />
        <Route path="payment/manual" element={<PaymentPage />} />
        <Route path="risk" element={<RiskPage />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="transactions" element={<TransactionsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
