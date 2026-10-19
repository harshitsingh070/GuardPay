// Typed REST client for the Spring Boot backend (Vite convention:
// VITE_API_BASE_URL from .env).

import type { RiskEvaluationRequest, RiskEvaluationResponse } from '@/types/risk';
import type {
  CreatePayPalOrderRequest,
  PayPalCaptureResponse,
  PayPalGateResponse,
  PayPalOrderResponse,
} from '@/types/paypal';
import type { GuardedPaymentRequest, GuardedPaymentResponse } from '@/types/payment';

export const apiBaseUrl: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/** Error carrying the HTTP status plus the backend's message when available. */
export class ApiRequestError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const { headers, ...rest } = init;
  const res = await fetch(`${apiBaseUrl}${path}`, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(headers ?? {}),
    },
  });
  if (!res.ok) {
    throw await toApiError(res);
  }
  return (await res.json()) as T;
}

async function toApiError(res: Response): Promise<ApiRequestError> {
  let message = `Request failed (${res.status})`;
  try {
    const body = (await res.json()) as { message?: string };
    if (body && typeof body.message === 'string' && body.message.length > 0) {
      message = body.message;
    }
  } catch {
    // Non-JSON error body — keep the generic message.
  }
  return new ApiRequestError(res.status, message);
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
 */
export function createGuardedPayment(
  request: GuardedPaymentRequest,
): Promise<GuardedPaymentResponse> {
  return apiFetch<GuardedPaymentResponse>('/api/payments/guarded', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

/** Captures an approved order. Allowed only by the stored server-side decision. */
export async function capturePayPalOrder(orderId: string): Promise<PayPalCaptureResponse> {
  const res = await fetch(`${apiBaseUrl}/api/paypal/orders/${encodeURIComponent(orderId)}/capture`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status === 403) {
    try {
      const gate = (await res.json()) as unknown;
      if (isGateResponse(gate)) throw new PayPalGateError(gate);
    } catch (err) {
      if (err instanceof PayPalGateError) throw err;
    }
  }
  if (!res.ok) throw await toApiError(res);
  return (await res.json()) as PayPalCaptureResponse;
}
