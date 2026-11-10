import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Ban,
  BellRing,
  Brain,
  CheckCircle2,
  Gauge,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  Loader2,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Timer,
  Wallet,
} from 'lucide-react';
import { usePageTitle } from '@/hooks/usePageTitle';
import { useAlerts } from '@/hooks/useAlerts';
import { ApiRequestError } from '@/services/api-client';
import { classifyFraudPattern } from '@/utils/fraud-pattern';
import {
  disableTotp,
  enrollTotp,
  getAiEnabled,
  getBehavioralProfile,
  getBehavioralSummary,
  getDashboardSummary,
  getTotpStatus,
  setAiEnabled,
} from '@/services/api-client';
import type { BehavioralProfile, BehavioralProfileSummary } from '@/types/index';
import type { DashboardSummary } from '@/types/dashboard';
import type { TotpStatus } from '@/types/mfa';

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
];

const PIPELINE = [
  'Capture the payment context — amount, recipient, message, link, and call state.',
  'Apply deterministic rules and compare the payment with stored history.',
  'Attach AI evidence when available, without letting it override the rules.',
  'Store the final ALLOW, VERIFY, or STOP decision for auditing.',
];

function money(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(value);
}

function messageOf(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message;
  return 'Unable to load dashboard metrics.';
}

const SEVERITY_STYLES: Record<string, string> = {
  CRITICAL: 'border-red-200 bg-red-50 text-red-900',
  HIGH: 'border-orange-200 bg-orange-50 text-orange-900',
  WARNING: 'border-amber-200 bg-amber-50 text-amber-900',
};

export default function DashboardPage() {
  usePageTitle('Dashboard');
  const { alerts, connection } = useAlerts();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [aiEnabled, setAiEnabledState] = useState<boolean | null>(null);
  const [aiBusy, setAiBusy] = useState(false);
  const [totp, setTotp] = useState<TotpStatus | null>(null);
  const [mfaBusy, setMfaBusy] = useState(false);
  const [mfaError, setMfaError] = useState<string | null>(null);
  const [behavioralProfile, setBehavioralProfile] = useState<BehavioralProfile | null>(null);
  const [behavioralSummary, setBehavioralSummary] = useState<BehavioralProfileSummary | null>(null);
  const [behavioralLoading, setBehavioralLoading] = useState(true);

  const load = useCallback(() => {
    setReload((current) => current + 1);
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    getDashboardSummary()
      .then((result) => {
        if (active) {
          setSummary(result);
          setError(null);
        }
      })
      .catch((reason: unknown) => {
        if (active) setError(messageOf(reason));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reload]);

  useEffect(() => {
    if (alerts.length === 0) return;
    const timer = setTimeout(load, 600);
    return () => clearTimeout(timer);
  }, [alerts, load]);

  useEffect(() => {
    const timer = setInterval(load, 15_000);
    return () => clearInterval(timer);
  }, [load]);

  useEffect(() => {
    let active = true;
    Promise.all([getAiEnabled(), getTotpStatus(), getBehavioralProfile(), getBehavioralSummary()])
      .then(([ai, mfa, profile, summary]) => {
        if (!active) return;
        setAiEnabledState(ai.enabled);
        setTotp(mfa);
        setBehavioralProfile(profile);
        setBehavioralSummary(summary);
      })
      .catch(() => {
        if (active) setAiEnabledState(null);
        if (active) setBehavioralProfile(null);
        if (active) setBehavioralSummary(null);
      })
      .finally(() => {
        if (active) setBehavioralLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function toggleAi() {
    if (aiBusy) return;
    setAiBusy(true);
    try {
      const next = await setAiEnabled(!(aiEnabled ?? true));
      setAiEnabledState(next.enabled);
      load();
    } catch (reason) {
      setError(messageOf(reason));
    } finally {
      setAiBusy(false);
    }
  }

  async function enrollMfa() {
    if (mfaBusy) return;
    setMfaBusy(true);
    setMfaError(null);
    try {
      const result = await enrollTotp();
      setTotp(result);
    } catch (reason) {
      setMfaError(messageOf(reason));
    } finally {
      setMfaBusy(false);
    }
  }

  async function disableMfa() {
    if (mfaBusy) return;
    setMfaBusy(true);
    setMfaError(null);
    try {
      const result = await disableTotp();
      setTotp(result);
    } catch (reason) {
      setMfaError(messageOf(reason));
    } finally {
      setMfaBusy(false);
    }
  }

  const allow = summary?.allowCount ?? 0;
  const verify = summary?.verifyCount ?? 0;
  const stop = summary?.stopCount ?? 0;
  const decided = Math.max(allow + verify + stop, 1);

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
              Live decisions, latency, alert delivery, and verification health for this workspace.
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-semibold ${
              connection === 'live'
                ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                : 'border-guardpay-line bg-white text-guardpay-muted'
            }`}
          >
            <span
              className={`h-1.5 w-1.5 rounded-full ${
                connection === 'live' ? 'bg-emerald-500' : 'bg-guardpay-muted'
              }`}
              aria-hidden="true"
            />
            {connection === 'live' ? 'Live alerts connected' : 'Alert stream reconnecting'}
          </span>
          <button
            type="button"
            onClick={load}
            className="inline-flex items-center gap-2 rounded-full border border-guardpay-line bg-white px-3.5 py-2 text-sm font-semibold text-guardpay-ink transition-colors hover:border-guardpay-blue/40 hover:bg-guardpay-canvas"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            Refresh
          </button>
        </div>
      </div>

      {error !== null && (
        <div
          className="mt-6 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
          role="alert"
        >
          {error}
        </div>
      )}

      {summary === null && loading && (
        <div className="gp-card mt-6 flex items-center gap-3 p-6" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-guardpay-blue" aria-hidden="true" />
          <span className="text-sm text-guardpay-muted">Loading live metrics…</span>
        </div>
      )}

      {summary !== null && (
        <>
          <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              Icon={ShieldCheck}
              label="Decisions reviewed"
              value={String(summary.decisions)}
              hint={`Average score ${summary.averageScore.toFixed(1)} / 100`}
            />
            <MetricCard
              Icon={Ban}
              label="Blocked value"
              value={money(summary.blockedAmount)}
              hint={`${stop} STOP decision${stop === 1 ? '' : 's'}`}
            />
            <MetricCard
              Icon={Timer}
              label="Decision latency"
              value={`${summary.latency.p95Ms} ms`}
              hint={`p95 · p50 ${summary.latency.p50Ms} ms · ${summary.latency.samples} samples`}
            />
            <MetricCard
              Icon={Brain}
              label="AI availability"
              value={`${summary.ai.availabilityPct.toFixed(1)}%`}
              hint={`${summary.ai.available} available · ${summary.ai.unavailable} fallback`}
              action={
                <button
                  type="button"
                  onClick={toggleAi}
                  disabled={aiBusy}
                  className="rounded-full border border-guardpay-line bg-white px-2.5 py-1 text-[11px] font-semibold text-guardpay-ink transition-colors hover:bg-guardpay-canvas disabled:opacity-60"
                >
                  {aiBusy ? '…' : aiEnabled === false ? 'Enable AI' : 'Simulate AI outage'}
                </button>
              }
            />
          </div>

          <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <div className="gp-card p-5 sm:p-6">
              <div className="flex items-center justify-between gap-2">
                <h2 className="text-lg font-bold text-guardpay-ink">Decision mix</h2>
                <Activity className="h-4 w-4 text-guardpay-muted" aria-hidden="true" />
              </div>
              <div className="mt-4 space-y-3">
                <DecisionBar
                  label="Allow"
                  count={allow}
                  total={decided}
                  tone="bg-emerald-500"
                  text="text-emerald-700"
                />
                <DecisionBar
                  label="Verify"
                  count={verify}
                  total={decided}
                  tone="bg-amber-500"
                  text="text-amber-700"
                />
                <DecisionBar
                  label="Stop"
                  count={stop}
                  total={decided}
                  tone="bg-red-500"
                  text="text-red-700"
                />
              </div>
              <div className="mt-5 grid grid-cols-2 gap-3 border-t border-guardpay-line pt-4 text-sm">
                <div>
                  <p className="text-xs text-guardpay-muted">Verification success</p>
                  <p className="mt-0.5 text-xl font-bold tabular-nums text-guardpay-ink">
                    {summary.verification.successRatePct.toFixed(1)}%
                  </p>
                  <p className="text-[11px] text-guardpay-muted">
                    {summary.verification.verified} verified · {summary.verification.pending} pending
                  </p>
                </div>
                <div>
                  <p className="text-xs text-guardpay-muted">Failed / expired</p>
                  <p className="mt-0.5 text-xl font-bold tabular-nums text-guardpay-ink">
                    {summary.verification.failed + summary.verification.expired}
                  </p>
                  <p className="text-[11px] text-guardpay-muted">
                    {summary.verification.failed} failed · {summary.verification.expired} expired
                  </p>
                </div>
              </div>
            </div>

            <div className="gp-card p-5 sm:p-6">
              <div className="flex items-center justify-between gap-2">
                <h2 className="text-lg font-bold text-guardpay-ink">Alert delivery</h2>
                <BellRing className="h-4 w-4 text-guardpay-muted" aria-hidden="true" />
              </div>
              <div className="mt-4 grid grid-cols-3 divide-x divide-guardpay-line rounded-xl border border-guardpay-line">
                <div className="px-3 py-3">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                    Total
                  </p>
                  <p className="mt-1 text-xl font-bold tabular-nums text-guardpay-ink">
                    {summary.alerts.total}
                  </p>
                </div>
                <div className="px-3 py-3">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                    Critical
                  </p>
                  <p className="mt-1 text-xl font-bold tabular-nums text-red-700">
                    {summary.alerts.critical}
                  </p>
                </div>
                <div className="px-3 py-3">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                    p95 delivery
                  </p>
                  <p className="mt-1 text-xl font-bold tabular-nums text-guardpay-ink">
                    {summary.alerts.p95DeliveryMs === null
                      ? '—'
                      : `${summary.alerts.p95DeliveryMs} ms`}
                  </p>
                </div>
              </div>
              <p className="mt-3 text-xs text-guardpay-muted">
                {summary.alerts.sent} delivered · {summary.alerts.pending} queued in the outbox.
                Critical alerts enqueue email and SMS delivery.
              </p>

              <h3 className="mt-5 text-sm font-bold text-guardpay-ink">Recent alerts</h3>
              {alerts.length === 0 ? (
                <p className="mt-2 text-xs text-guardpay-muted">
                  No alerts in this session. Blocked or high-risk payments appear here instantly.
                </p>
              ) : (
                <ul className="mt-2 space-y-2">
                  {alerts.slice(0, 4).map((alert) => {
                    const fraudPattern = classifyFraudPattern({
                      codes: alert.signals,
                      action: alert.recommendedAction,
                    });
                    return (
                      <li
                        key={alert.id}
                        className={`rounded-xl border p-2.5 text-xs ${
                          SEVERITY_STYLES[alert.severity] ?? SEVERITY_STYLES.WARNING
                        }`}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-bold">{alert.title}</span>
                          {alert.score !== null && (
                            <span className="font-semibold tabular-nums">{alert.score}/100</span>
                          )}
                        </div>
                        <p className="mt-0.5 leading-relaxed">{alert.message}</p>
                        {fraudPattern !== null && (
                          <p className="mt-1 font-semibold">Likely: {fraudPattern.label}</p>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </div>

          {behavioralLoading ? (
            <div className="gp-card mt-6 flex items-center gap-3 p-6" role="status">
              <Loader2 className="h-5 w-5 animate-spin text-guardpay-blue" aria-hidden="true" />
              <span className="text-sm text-guardpay-muted">Loading behavioral profile…</span>
            </div>
          ) : behavioralProfile !== null ? (
            <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
              <div className="gp-card p-5 sm:p-6">
                <div className="flex items-center gap-2">
                  <Brain className="h-5 w-5 text-guardpay-blue" aria-hidden="true" />
                  <h2 className="text-lg font-bold text-guardpay-ink">Behavioral Intelligence</h2>
                  <span
                    className={`ml-auto rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                      behavioralProfile.profileStatus === 'ESTABLISHED'
                        ? 'bg-emerald-50 text-emerald-700'
                        : behavioralProfile.profileStatus === 'INITIALIZING'
                        ? 'bg-amber-50 text-amber-700'
                        : 'bg-guardpay-line text-guardpay-muted'
                    }`}
                  >
                    {behavioralProfile.profileStatus === 'ESTABLISHED'
                      ? 'Profile established'
                      : behavioralProfile.profileStatus === 'INITIALIZING'
                      ? 'Initializing'
                      : 'Insufficient data'}
                  </span>
                </div>
                <p className="mt-1 text-sm text-guardpay-muted">
                  GuardPay learns your normal payment behavior and flags personalized
                  deviations — amount, time, recipient, device, location, and frequency.
                </p>
                <div className="mt-4 space-y-3">
                  <BehavioralDimensionRow
                    label="Amount pattern"
                    status={behavioralProfile.amountBehavior?.availability ?? 'UNAVAILABLE'}
                    detail={
                      behavioralProfile.amountBehavior?.medianAmount != null
                        ? `Median: ${new Intl.NumberFormat('en-US', {
                            style: 'currency',
                            currency: 'USD',
                          }).format(behavioralProfile.amountBehavior.medianAmount)}`
                        : 'No history'
                    }
                  />
                  <BehavioralDimensionRow
                    label="Payment frequency"
                    status={
                      behavioralProfile.frequencyBehavior?.availability ?? 'UNAVAILABLE'
                    }
                    detail={
                      behavioralProfile.frequencyBehavior?.paymentsPerDay != null
                        ? `${behavioralProfile.frequencyBehavior.paymentsPerDay.toFixed(1)} / day`
                        : 'No history'
                    }
                  />
                  <BehavioralDimensionRow
                    label="Payment hours"
                    status={behavioralProfile.timeBehavior?.availability ?? 'UNAVAILABLE'}
                    detail={
                      behavioralProfile.timeBehavior?.peakHour != null
                        ? `Peak: ${String(behavioralProfile.timeBehavior.peakHour).padStart(2, '0')}:00`
                        : 'No history'
                    }
                  />
                  <BehavioralDimensionRow
                    label="Recipients"
                    status={
                      behavioralProfile.recipientBehavior?.availability ?? 'UNAVAILABLE'
                    }
                    detail={
                      behavioralProfile.recipientBehavior?.uniqueRecipientCount != null
                        ? `${behavioralProfile.recipientBehavior.uniqueRecipientCount} unique`
                        : 'No history'
                    }
                  />
                  <BehavioralDimensionRow
                    label="Devices"
                    status={behavioralProfile.deviceBehavior?.availability ?? 'UNAVAILABLE'}
                    detail={
                      behavioralProfile.deviceBehavior?.deviceCount != null
                        ? `${behavioralProfile.deviceBehavior.deviceCount} known`
                        : 'No history'
                    }
                  />
                  <BehavioralDimensionRow
                    label="Locations"
                    status={
                      behavioralProfile.locationBehavior?.availability ?? 'UNAVAILABLE'
                    }
                    detail={
                      behavioralProfile.locationBehavior?.geoCountryCount != null
                        ? `${behavioralProfile.locationBehavior.geoCountryCount} countries`
                        : 'No history'
                    }
                  />
                </div>
                {behavioralProfile.transactionCount != null && behavioralProfile.transactionCount > 0 && (
                  <p className="mt-3 text-xs text-guardpay-muted">
                    Based on {behavioralProfile.transactionCount} completed transaction(s).
                  </p>
                )}
                {behavioralSummary && behavioralSummary.recentAnomalies.length > 0 && (
                  <div className="mt-4 border-t border-guardpay-line pt-3">
                    <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                      Recent anomalies
                    </p>
                    <ul className="mt-2 space-y-1">
                      {behavioralSummary.recentAnomalies.map((anomaly) => (
                        <li key={anomaly} className="text-xs text-guardpay-ink">
                          {anomaly}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="gp-card mt-6 p-6 text-center text-guardpay-muted text-sm">
              No behavioral profile available yet. Complete payments to build your baseline.
            </div>
          )}

          <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <div className="gp-card p-5 sm:p-6">
              <div className="flex items-center gap-2">
                <KeyRound className="h-5 w-5 text-guardpay-blue" aria-hidden="true" />
                <h2 className="text-lg font-bold text-guardpay-ink">Second factor</h2>
                <span
                  className={`ml-auto rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                    totp?.enrolled
                      ? 'bg-emerald-50 text-emerald-700'
                      : 'bg-amber-50 text-amber-700'
                  }`}
                >
                  {totp?.enrolled ? 'TOTP active' : 'Simulated'}
                </span>
              </div>
              <p className="mt-1 text-sm text-guardpay-muted">
                When TOTP is enrolled, VERIFY payments ask for a live authenticator code instead of
                the simulated channel.
              </p>

              {totp?.enrolled ? (
                <div className="mt-4 space-y-2">
                  <p className="flex items-center gap-1.5 text-sm font-semibold text-emerald-700">
                    <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                    Authenticator enrolled
                  </p>
                  <button
                    type="button"
                    onClick={disableMfa}
                    disabled={mfaBusy}
                    className="rounded-full border border-guardpay-line bg-white px-3.5 py-2 text-sm font-semibold text-guardpay-ink transition-colors hover:bg-guardpay-canvas disabled:opacity-60"
                  >
                    {mfaBusy ? 'Disabling…' : 'Disable TOTP'}
                  </button>
                </div>
              ) : (
                <div className="mt-4">
                  <button
                    type="button"
                    onClick={enrollMfa}
                    disabled={mfaBusy}
                    className="inline-flex items-center gap-2 rounded-full bg-guardpay-blue px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[#005ea6] disabled:opacity-60"
                  >
                    {mfaBusy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                    {mfaBusy ? 'Enrolling…' : 'Enroll authenticator'}
                  </button>
                </div>
              )}

              {totp?.secret !== null && totp?.secret !== undefined && (
                <div className="mt-3 rounded-xl border border-guardpay-line bg-guardpay-canvas p-3 text-xs">
                  <p className="font-semibold text-guardpay-ink">Provisioning secret</p>
                  <p className="mt-1 break-all font-mono text-[11px] text-guardpay-muted">
                    {totp.secret}
                  </p>
                  {totp.otpauthUri !== null && (
                    <p className="mt-1 break-all font-mono text-[10px] text-guardpay-muted">
                      {totp.otpauthUri}
                    </p>
                  )}
                  <p className="mt-1 text-[11px] text-guardpay-muted">
                    Add this to any TOTP authenticator app. The secret is shown only here.
                  </p>
                </div>
              )}

              {mfaError !== null && (
                <p className="mt-3 flex items-center gap-1.5 text-xs text-red-700" role="alert">
                  <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
                  {mfaError}
                </p>
              )}
            </div>

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
          </div>
        </>
      )}

      <div className="mt-6 gp-card p-5 sm:p-6">
        <div className="flex items-center gap-2">
          <Sparkles className="h-5 w-5 text-guardpay-blue" aria-hidden="true" />
          <h2 className="text-lg font-bold text-guardpay-ink">How a decision is made</h2>
        </div>
        <p className="mt-1 text-sm text-guardpay-muted">
          The same server-side pipeline powers every checkout and manual evaluation.
        </p>
        <ol className="mt-4 grid gap-3 sm:grid-cols-2">
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

      <div className="mt-6 grid gap-3 sm:grid-cols-3">
        <SmallStat Icon={Gauge} label="Average risk score" value={summary?.averageScore.toFixed(1) ?? '—'} />
        <SmallStat
          Icon={Wallet}
          label="Sandbox volume"
          value={summary === null ? '—' : `${summary.decisions} payments`}
        />
        <SmallStat
          Icon={Ban}
          label="Blocked value"
          value={summary === null ? '—' : money(summary.blockedAmount)}
        />
      </div>
    </section>
  );
}

function MetricCard({
  Icon,
  label,
  value,
  hint,
  action,
}: {
  Icon: typeof ShieldCheck;
  label: string;
  value: string;
  hint: string;
  action?: ReactNode;
}) {
  return (
    <div className="gp-card p-5">
      <div className="flex items-center justify-between gap-2">
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-guardpay-canvas text-guardpay-muted">
          <Icon className="h-4 w-4" aria-hidden="true" />
        </span>
        {action}
      </div>
      <p className="mt-3 text-2xl font-bold tabular-nums text-guardpay-ink">{value}</p>
      <p className="mt-1 text-sm font-semibold text-guardpay-ink">{label}</p>
      <p className="mt-0.5 text-xs text-guardpay-muted">{hint}</p>
    </div>
  );
}

function DecisionBar({
  label,
  count,
  total,
  tone,
  text,
}: {
  label: string;
  count: number;
  total: number;
  tone: string;
  text: string;
}) {
  const width = Math.round((count / total) * 100);
  return (
    <div>
      <div className="flex items-center justify-between text-xs">
        <span className={`font-semibold ${text}`}>{label}</span>
        <span className="tabular-nums text-guardpay-muted">
          {count} · {width}%
        </span>
      </div>
      <div className="mt-1 h-2 overflow-hidden rounded-full bg-guardpay-canvas">
        <div className={`h-full rounded-full ${tone}`} style={{ width: `${width}%` }} />
      </div>
    </div>
  );
}

function SmallStat({
  Icon,
  label,
  value,
}: {
  Icon: typeof Gauge;
  label: string;
  value: string;
}) {
  return (
    <div className="gp-card flex items-center gap-3 p-4">
      <span className="grid h-9 w-9 place-items-center rounded-xl bg-guardpay-canvas text-guardpay-muted">
        <Icon className="h-4 w-4" aria-hidden="true" />
      </span>
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-guardpay-muted">
          {label}
        </p>
        <p className="text-lg font-bold tabular-nums text-guardpay-ink">{value}</p>
      </div>
    </div>
  );
}

function BehavioralDimensionRow({
  label,
  status,
  detail,
}: {
  label: string;
  status: string;
  detail: string;
}) {
  const isAvailable = status === 'AVAILABLE';
  return (
    <div className="flex items-center justify-between gap-3 p-2.5 rounded-xl border border-guardpay-line bg-white">
      <div className="flex-1 min-w-0">
        <p className="text-xs font-semibold text-guardpay-ink">{label}</p>
        <p className="text-[10px] text-guardpay-muted">{detail}</p>
      </div>
      <span
        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
          isAvailable
            ? 'bg-emerald-50 text-emerald-700'
            : status === 'INITIALIZING'
            ? 'bg-amber-50 text-amber-700'
            : 'bg-guardpay-line text-guardpay-muted'
        }`}
      >
        {isAvailable ? 'Available' : status === 'INITIALIZING' ? 'Initializing' : 'Unavailable'}
      </span>
    </div>
  );
}
