// Typed REST client for the Spring Boot backend (Vite convention:
// VITE_API_BASE_URL from .env). The Android shell supplies its build-time
// backend URL through the native bridge so the bundled UI stays portable.

import { readNative } from '@/services/native-bridge';
import type { RiskEvaluationRequest, RiskEvaluationResponse } from '@/types/risk';
import type {
  CreatePayPalOrderRequest,
  PayPalCaptureResponse,
  PayPalGateResponse,
  PayPalOrderResponse,
} from '@/types/paypal';
import type {
  GuardedPaymentRequest,
  GuardedPaymentResponse,
  VerificationConfirmResponse,
} from '@/types/payment';
import type { TransactionDetail, TransactionPage } from '@/types/transaction';

function resolveApiBaseUrl(): string {
  const nativeUrl = readNative((bridge) => bridge.getApiBaseUrl().trim(), '');
  const base = nativeUrl !== '' ? nativeUrl : (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080');
  return base.replace(/\/+$/, '');
}

export const apiBaseUrl: string = resolveApiBaseUrl();

/** Payment evaluation can include two LLM calls, so allow up to 90 seconds. */
const DEFAULT_TIMEOUT_MS = 90_000;

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

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const { headers, ...rest } = init;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${apiBaseUrl}${path}`, {
      ...rest,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(headers ?? {}),
      },
    });
    if (!res.ok) {
      throw await toApiError(res);
    }
    return (await res.json()) as T;
  } catch (error) {
    if (error instanceof ApiRequestError) throw error;
    if (isAbortError(error)) {
      throw new ApiRequestError(0, 'The security check timed out. Please try again.');
    }
    throw new ApiRequestError(0, 'Unable to reach GuardPay. Check your connection and try again.');
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Submits payment context for evaluation. No scoring happens here — the
 * backend DeterministicRiskEngine is the only source of truth.
 */
export function evaluateRisk(request: RiskEvaluationRequest): Promise<RiskEvaluationResponse> {
  return apiFetch<RiskEvaluationResponse>('/api/risk/evaluate', {
    method: 'POST',
    body: JSON.stringify(request),
  });
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
 * Creates a PayPal Sandbox order (moves no money). The backend re-runs risk
 * evaluation server-side and decides whether an order may be created.
 */
export function createPayPalOrder(
  request: CreatePayPalOrderRequest,
): Promise<PayPalOrderResponse> {
  return apiFetch<PayPalOrderResponse>('/api/paypal/orders', {
    method: 'POST',
    body: JSON.stringify(request),
  });
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

/** Paged transaction history. The optional user id scopes the query server-side. */
export function listTransactions(
  userId: string | undefined,
  page: number,
  size: number,
): Promise<TransactionPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (userId !== undefined) params.set('userId', userId);
  return apiFetch<TransactionPage>(`/api/transactions?${params.toString()}`);
}

/** Full detail for one transaction, including the stored decision and findings. */
export function getTransaction(
  id: string,
  userId: string | undefined,
): Promise<TransactionDetail> {
  const params = new URLSearchParams();
  if (userId !== undefined) params.set('userId', userId);
  const query = params.toString();
  return apiFetch<TransactionDetail>(
    `/api/transactions/${encodeURIComponent(id)}${query === '' ? '' : `?${query}`}`,
  );
}

/** Captures an approved order. Allowed only by the stored server-side decision. */
export async function capturePayPalOrder(orderId: string): Promise<PayPalCaptureResponse> {
  const res = await fetch(
    `${apiBaseUrl}/api/paypal/orders/${encodeURIComponent(orderId)}/capture`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    },
  );
  const body = await readJson(res);
  if (res.status === 403 && isGateResponse(body)) {
    throw new PayPalGateError(body);
  }
  if (!res.ok) throw await toApiError(res, body);
  return body as PayPalCaptureResponse;
}
