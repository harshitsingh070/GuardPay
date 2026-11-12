// Typed REST client for the Spring Boot backend (Vite convention:
// VITE_API_BASE_URL from .env). The Android shell supplies its build-time
// backend URL through the native bridge so the bundled UI stays portable.

import { readNative } from '@/services/native-bridge';
import type { Alert } from '@/types/alerts';
import type {
  BehavioralProfile,
  BehavioralProfileSummary,
  HighValueBaseline,
} from '@/types/index';
import type { DashboardSummary } from '@/types/dashboard';
import type {
  FeedbackMetrics,
  FeedbackPage,
  FeedbackReviewAction,
  FeedbackSummary,
  FraudFeedback,
  FraudReportResponse,
  LearningDataset,
  LearningEvent,
  LearningModel,
  LearningOverview,
  RecipientReputationSummary,
  SubmitFeedbackInput,
  TrainingResult,
} from '@/types/intelligence';
import type { PayPalCaptureResponse, PayPalGateResponse } from '@/types/paypal';
import type {
  GuardedPaymentRequest,
  GuardedPaymentResponse,
  VerificationConfirmResponse,
} from '@/types/payment';
import type { TotpEnrollment, TotpStatus } from '@/types/mfa';
import type { TransactionDetail, TransactionPage } from '@/types/transaction';

function resolveApiBaseUrl(): string {
  const nativeUrl = readNative((bridge) => bridge.getApiBaseUrl().trim(), '');
  if (nativeUrl !== '') return nativeUrl.replace(/\/+$/, '');

  const configured = (import.meta.env.VITE_API_BASE_URL ?? '').trim();
  if (configured !== '') return configured.replace(/\/+$/, '');

  // Blank config means "same host the page was opened from" (localhost or a
  // LAN IP), with the backend on its default port. Empty strings are not
  // nullish, so they must be handled explicitly or requests would be sent to
  // the frontend dev server and 404.
  if (typeof window !== 'undefined' && window.location.hostname !== '') {
    const { protocol, hostname } = window.location;
    return `${protocol}//${hostname}:8080`;
  }
  return 'http://localhost:8080';
}

export const apiBaseUrl: string = resolveApiBaseUrl();

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  admin: boolean;
}

export interface AuthSession {
  authenticated: boolean;
  user: AuthUser | null;
  csrfToken?: string | null;
}

/** Payment evaluation can include two LLM calls, so allow up to 90 seconds. */
const DEFAULT_TIMEOUT_MS = 90_000;
const CSRF_HEADER = 'X-CSRF-Token';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

let csrfToken: string | null = null;
let csrfRequest: Promise<string | null> | null = null;

/** Error carrying the HTTP status plus the backend's message when available. */
export class ApiRequestError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

function messageFrom(body: unknown): string | null {
  if (typeof body !== 'object' || body === null) return null;
  const message = (body as Record<string, unknown>).message;
  return typeof message === 'string' && message.length > 0 ? message : null;
}

async function readJson(res: Response): Promise<unknown> {
  try {
    return await res.json();
  } catch {
    return undefined;
  }
}

async function toApiError(res: Response, body?: unknown): Promise<ApiRequestError> {
  const parsed = body === undefined ? await readJson(res) : body;
  return new ApiRequestError(
    res.status,
    messageFrom(parsed) ?? `Request failed (${res.status})`,
  );
}

function isAbortError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    (error as { name?: unknown }).name === 'AbortError'
  );
}

function rememberCsrfToken(body: unknown): void {
  if (typeof body !== 'object' || body === null) return;
  const candidate = (body as Record<string, unknown>).csrfToken;
  if (typeof candidate === 'string' && candidate.length > 0) {
    csrfToken = candidate;
  }
}

function isCsrfFailure(body: unknown): boolean {
  return messageFrom(body) === 'Invalid CSRF token';
}

/** Exposed for the PayPal capture path, which needs its own gate response handling. */
export function getCsrfToken(): string | null {
  return csrfToken;
}

/** Stores a token returned by login/me/csrf so mutations can echo it. */
export function setCsrfToken(token: string | null | undefined): void {
  csrfToken = typeof token === 'string' && token.length > 0 ? token : null;
}

async function refreshCsrfToken(): Promise<string | null> {
  csrfToken = null;
  return ensureCsrfToken();
}

/** Fetches the session-bound token once, then reuses it for every mutation. */
export async function ensureCsrfToken(): Promise<string | null> {
  if (csrfToken !== null) return csrfToken;
  if (csrfRequest !== null) return csrfRequest;

  csrfRequest = (async () => {
    try {
      const res = await fetch(`${apiBaseUrl}/api/auth/csrf`, {
        credentials: 'include',
        headers: { Accept: 'application/json' },
      });
      const body = await readJson(res);
      if (res.ok) rememberCsrfToken(body);
      return csrfToken;
    } catch {
      return null;
    } finally {
      csrfRequest = null;
    }
  })();

  return csrfRequest;
}

interface FetchResult {
  res: Response;
  body: unknown;
}

async function performFetch(
  path: string,
  init: RequestInit,
  timeoutMs: number,
  csrf: string | null,
): Promise<FetchResult> {
  const { headers, ...rest } = init;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${apiBaseUrl}${path}`, {
      ...rest,
      credentials: 'include',
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(csrf === null ? {} : { [CSRF_HEADER]: csrf }),
        ...(headers ?? {}),
      },
    });
    const body = res.status === 204 ? undefined : await readJson(res);
    return { res, body };
  } finally {
    clearTimeout(timer);
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const needsCsrf = !SAFE_METHODS.has(method);
  let csrf = needsCsrf ? await ensureCsrfToken() : null;

  try {
    let result = await performFetch(path, init, timeoutMs, csrf);

    // A session rotation in another tab invalidates the old token. Refresh the
    // token and retry once instead of surfacing a spurious failure.
    if (needsCsrf && result.res.status === 403 && isCsrfFailure(result.body)) {
      const refreshed = await refreshCsrfToken();
      if (refreshed !== null && refreshed !== csrf) {
        csrf = refreshed;
        result = await performFetch(path, init, timeoutMs, csrf);
      }
    }

    if (!result.res.ok) {
      throw await toApiError(result.res, result.body);
    }
    rememberCsrfToken(result.body);
    return result.body as T;
  } catch (error) {
    if (error instanceof ApiRequestError) throw error;
    if (isAbortError(error)) {
      throw new ApiRequestError(0, 'The security check timed out. Please try again.');
    }
    throw new ApiRequestError(0, 'Unable to reach GuardPay. Check your connection and try again.');
  }
}

/** Error for a risk-gate refusal — carries the server's gate body. */
export class PayPalGateError extends ApiRequestError {
  readonly gate: PayPalGateResponse;

  constructor(gate: PayPalGateResponse) {
    super(403, gate.message);
    this.name = 'PayPalGateError';
    this.gate = gate;
  }
}

function isGateResponse(value: unknown): value is PayPalGateResponse {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    candidate.allowed === false &&
    typeof candidate.action === 'string' &&
    typeof candidate.message === 'string'
  );
}

/**
 * Starts a guarded payment. Sends business context only — amounts, names,
 * optional message/URL. Risk is derived server-side; nothing here can force
 * a decision.
 *
 * The idempotency key guarantees a retried delivery of the same attempt
 * returns the stored result instead of creating a second PayPal order.
 */
export function createGuardedPayment(
  request: GuardedPaymentRequest,
  idempotencyKey?: string,
): Promise<GuardedPaymentResponse> {
  return apiFetch<GuardedPaymentResponse>('/api/payments/guarded', {
    method: 'POST',
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    body: JSON.stringify(request),
  });
}

/**
 * Answers a controlled VERIFY challenge. The backend alone records the
 * outcome and only creates the PayPal order once verification succeeds.
 */
export function confirmPaymentVerification(
  paymentId: string,
  code: string,
): Promise<VerificationConfirmResponse> {
  return apiFetch<VerificationConfirmResponse>(
    `/api/payments/${encodeURIComponent(paymentId)}/verification/confirm`,
    {
      method: 'POST',
      body: JSON.stringify({ code }),
    },
  );
}

/** Paged transaction history for the server-side demo workspace. */
export function listTransactions(page: number, size: number): Promise<TransactionPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiFetch<TransactionPage>(`/api/transactions?${params.toString()}`);
}

/** Full detail for one transaction, including the stored decision and findings. */
export function getTransaction(id: string): Promise<TransactionDetail> {
  return apiFetch<TransactionDetail>(`/api/transactions/${encodeURIComponent(id)}`);
}

/**
 * Reports a recipient for suspected fraud. The transaction must belong to the
 * caller; the backend derives and hashes the recipient identity server-side.
 */
export function submitFraudReport(request: {
  relatedTransactionId?: string;
  incidentAt?: string;
}): Promise<FraudReportResponse> {
  return apiFetch<FraudReportResponse>('/api/intelligence/recipients/reports', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

/** Explicit ground-truth label for a transaction decision. */
export function submitFraudFeedback(
  transactionId: string,
  input: SubmitFeedbackInput,
): Promise<FraudFeedback> {
  return apiFetch<FraudFeedback>(
    `/api/fraud-feedback/transactions/${encodeURIComponent(transactionId)}`,
    {
      method: 'POST',
      body: JSON.stringify(input),
    },
  );
}

/** The caller's existing feedback for a transaction, if any. */
export async function getFraudFeedback(
  transactionId: string,
): Promise<FraudFeedback | null> {
  try {
    return await apiFetch<FraudFeedback>(
      `/api/fraud-feedback/transactions/${encodeURIComponent(transactionId)}`,
    );
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

/** Admin feedback queue (server enforces the admin role). */
export function listAdminFeedback(
  status: string | null,
  page = 0,
  size = 50,
): Promise<FeedbackPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status !== null) params.set('status', status);
  return apiFetch<FeedbackPage>(`/api/admin/fraud-feedback?${params.toString()}`);
}

export function getFeedbackSummary(): Promise<FeedbackSummary> {
  return apiFetch<FeedbackSummary>('/api/admin/fraud-feedback/summary');
}

export function getFeedbackMetrics(): Promise<FeedbackMetrics> {
  return apiFetch<FeedbackMetrics>('/api/admin/fraud-feedback/metrics');
}

export function reviewFeedback(
  feedbackId: string,
  action: FeedbackReviewAction,
  note?: string,
): Promise<unknown> {
  return apiFetch<unknown>(
    `/api/admin/fraud-feedback/${encodeURIComponent(feedbackId)}/review`,
    {
      method: 'POST',
      body: JSON.stringify({ action, note }),
    },
  );
}

export function getLearningOverview(): Promise<LearningOverview> {
  return apiFetch<LearningOverview>('/api/admin/learning/overview');
}

export function listLearningDatasets(): Promise<LearningDataset[]> {
  return apiFetch<LearningDataset[]>('/api/admin/learning/datasets');
}

export function buildLearningDataset(): Promise<LearningDataset> {
  return apiFetch<LearningDataset>('/api/admin/learning/datasets', { method: 'POST' });
}

export function trainLearningModel(datasetId: string): Promise<TrainingResult> {
  return apiFetch<TrainingResult>(
    `/api/admin/learning/datasets/${encodeURIComponent(datasetId)}/train`,
    { method: 'POST' },
  );
}

export function listLearningModels(): Promise<LearningModel[]> {
  return apiFetch<LearningModel[]>('/api/admin/learning/models');
}

export function approveLearningModel(modelId: string): Promise<LearningModel> {
  return apiFetch<LearningModel>(
    `/api/admin/learning/models/${encodeURIComponent(modelId)}/approve`,
    { method: 'POST' },
  );
}

export function rejectLearningModel(modelId: string): Promise<LearningModel> {
  return apiFetch<LearningModel>(
    `/api/admin/learning/models/${encodeURIComponent(modelId)}/reject`,
    { method: 'POST' },
  );
}

export function deployLearningModel(modelId: string): Promise<LearningModel> {
  return apiFetch<LearningModel>(
    `/api/admin/learning/models/${encodeURIComponent(modelId)}/deploy`,
    { method: 'POST' },
  );
}

export function rollbackLearningModel(modelId: string): Promise<LearningModel> {
  return apiFetch<LearningModel>(
    `/api/admin/learning/models/${encodeURIComponent(modelId)}/rollback`,
    { method: 'POST' },
  );
}

export function listLearningEvents(): Promise<LearningEvent[]> {
  return apiFetch<LearningEvent[]>('/api/admin/learning/events');
}

/** Safe, evidence-only reputation summary for a recipient the caller paid. */
export function getRecipientReputationSummary(
  recipientReference: string,
): Promise<RecipientReputationSummary> {
  return apiFetch<RecipientReputationSummary>(
    `/api/intelligence/recipients/${encodeURIComponent(recipientReference)}/summary`,
  );
}

/** Recent in-app alerts for the demo workspace. */
export function listAlerts(limit = 20): Promise<Alert[]> {
  return apiFetch<Alert[]>(`/api/alerts?limit=${limit}`);
}

/** Live dashboard aggregates. */
export function getDashboardSummary(): Promise<DashboardSummary> {
  return apiFetch<DashboardSummary>('/api/dashboard/summary');
}

/** Whether the demo account has an authenticator app enrolled. */
export function getTotpStatus(): Promise<TotpStatus> {
  return apiFetch<TotpStatus>('/api/mfa/totp');
}

/** Enrolls (or re-enrolls) the demo account and returns the provisioning secret. */
export function enrollTotp(): Promise<TotpEnrollment> {
  return apiFetch<TotpEnrollment>('/api/mfa/totp/enroll', { method: 'POST' });
}

/** Removes the TOTP secret, falling back to the next configured channel. */
export function disableTotp(): Promise<TotpStatus> {
  return apiFetch<TotpStatus>('/api/mfa/totp', { method: 'DELETE' });
}

/** Current cookie-backed session; never throws for an anonymous visitor. */
export function getAuthSession(): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/auth/me');
}

export function login(email: string, password: string): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function register(
  email: string,
  password: string,
  displayName: string,
): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, displayName }),
  });
}

export function logout(): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/auth/logout', { method: 'POST' });
}

/** Loopback-only demo switch; the backend rejects remote callers. */
export function getAiEnabled(): Promise<{ enabled: boolean }> {
  return apiFetch<{ enabled: boolean }>('/api/dev/ai');
}

/** Loopback-only demo switch; the backend rejects remote callers. */
export function setAiEnabled(enabled: boolean): Promise<{ enabled: boolean }> {
  return apiFetch<{ enabled: boolean }>('/api/dev/ai', {
    method: 'POST',
    body: JSON.stringify({ enabled }),
  });
}

/** Read-only behavioral profile for the authenticated user. */
export function getBehavioralProfile(): Promise<BehavioralProfile> {
  return apiFetch<BehavioralProfile>('/api/behavior/profile');
}

/** Safe behavioral summary for dashboard display. */
export function getBehavioralSummary(): Promise<BehavioralProfileSummary> {
  return apiFetch<BehavioralProfileSummary>('/api/behavior/summary');
}

/** Currency-specific high-value spending baseline for the authenticated user. */
export function getHighValueBaseline(currency = 'USD'): Promise<HighValueBaseline> {
  const params = new URLSearchParams({ currency });
  return apiFetch<HighValueBaseline>(`/api/behavior/high-value/baseline?${params.toString()}`);
}

/** Captures an approved order. Allowed only by the stored server-side decision. */
export async function capturePayPalOrder(orderId: string): Promise<PayPalCaptureResponse> {
  const url = `${apiBaseUrl}/api/paypal/orders/${encodeURIComponent(orderId)}/capture`;
  const send = async (csrf: string | null) => {
    const res = await fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(csrf === null ? {} : { [CSRF_HEADER]: csrf }),
      },
    });
    const body = await readJson(res);
    return { res, body };
  };

  let csrf = await ensureCsrfToken();
  let result = await send(csrf);
  if (result.res.status === 403 && isCsrfFailure(result.body)) {
    const refreshed = await refreshCsrfToken();
    if (refreshed !== null && refreshed !== csrf) {
      csrf = refreshed;
      result = await send(csrf);
    }
  }
  if (result.res.status === 403 && isGateResponse(result.body)) {
    throw new PayPalGateError(result.body);
  }
  if (!result.res.ok) throw await toApiError(result.res, result.body);
  return result.body as PayPalCaptureResponse;
}
