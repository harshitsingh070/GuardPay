import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { LockKeyhole, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { usePageTitle } from '@/hooks/usePageTitle';
import { ApiRequestError } from '@/services/api-client';

type Mode = 'login' | 'register';

interface LocationState {
  from?: string;
}

const inputClass =
  'mt-1.5 w-full rounded-xl border border-guardpay-line bg-white px-3.5 py-2.5 text-sm text-guardpay-ink shadow-sm outline-none transition focus:border-guardpay-blue focus:ring-2 focus:ring-guardpay-blue/20';

export default function LoginPage() {
  usePageTitle('Sign in');
  const { user, status, login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as LocationState | null)?.from ?? '/dashboard';

  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated' && user !== null) {
    return <Navigate to={from} replace />;
  }

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setPassword('');
    setConfirmPassword('');
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (mode === 'register' && password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(email.trim(), password);
      } else {
        await register(email.trim(), password, displayName.trim());
      }
      navigate(from, { replace: true });
    } catch (failure) {
      setError(
        failure instanceof ApiRequestError
          ? failure.message
          : 'Unable to sign in. Please try again.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#f8fafc] px-4 py-10 text-foreground">
      <section className="w-full max-w-md rounded-2xl border border-guardpay-line bg-white p-6 shadow-xl shadow-guardpay-navy/5 sm:p-8">
        <div className="flex items-center gap-2.5">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-guardpay-blue to-guardpay-sky text-white shadow-sm shadow-guardpay-blue/25">
            <ShieldCheck className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="text-lg font-bold tracking-tight text-guardpay-ink">GuardPay</p>
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-guardpay-muted">
              Payment safety
            </p>
          </div>
        </div>

        <div className="mt-6 flex rounded-xl bg-guardpay-canvas p-1" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            onClick={() => switchMode('login')}
            className={`flex-1 rounded-lg px-3 py-2 text-sm font-semibold transition ${
              mode === 'login'
                ? 'bg-white text-guardpay-navy shadow-sm'
                : 'text-guardpay-muted hover:text-guardpay-ink'
            }`}
          >
            Sign in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            onClick={() => switchMode('register')}
            className={`flex-1 rounded-lg px-3 py-2 text-sm font-semibold transition ${
              mode === 'register'
                ? 'bg-white text-guardpay-navy shadow-sm'
                : 'text-guardpay-muted hover:text-guardpay-ink'
            }`}
          >
            Create account
          </button>
        </div>

        <div className="mt-5 flex items-start gap-2 rounded-xl border border-[#dbeafe] bg-[#eff6ff] px-3.5 py-3 text-xs leading-relaxed text-guardpay-navy">
          <LockKeyhole className="mt-0.5 h-4 w-4 shrink-0 text-guardpay-blue" aria-hidden="true" />
          <p>Your payments, recipients, and alerts are private to your account.</p>
        </div>

        <form className="mt-5 space-y-4" onSubmit={handleSubmit}>
          {mode === 'register' && (
            <label className="block text-sm font-medium text-guardpay-ink">
              Display name
              <input
                className={inputClass}
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                autoComplete="name"
                maxLength={120}
                placeholder="Alex"
              />
            </label>
          )}

          <label className="block text-sm font-medium text-guardpay-ink">
            Email
            <input
              className={inputClass}
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
              maxLength={254}
              placeholder="you@example.com"
            />
          </label>

          <label className="block text-sm font-medium text-guardpay-ink">
            Password
            <input
              className={inputClass}
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={8}
              maxLength={72}
              placeholder="At least 8 characters"
            />
          </label>

          {mode === 'register' && (
            <label className="block text-sm font-medium text-guardpay-ink">
              Confirm password
              <input
                className={inputClass}
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                autoComplete="new-password"
                required
                minLength={8}
                maxLength={72}
              />
            </label>
          )}

          {error !== null && (
            <p
              role="alert"
              className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs font-medium text-red-700"
            >
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting || status === 'loading'}
            className="inline-flex min-h-11 w-full items-center justify-center rounded-full bg-guardpay-blue px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#005ea6] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting
              ? 'Please wait…'
              : mode === 'login'
                ? 'Sign in'
                : 'Create account'}
          </button>
        </form>

        <p className="mt-4 text-center text-[11px] leading-relaxed text-guardpay-muted">
          GuardPay never receives your PayPal password or card details.
        </p>
      </section>
    </div>
  );
}
