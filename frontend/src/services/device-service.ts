// Device call-state intake. The browser cannot read real telephony state, so
// the UI reports a user-confirmed signal; the backend still stores it under
// the development pairing token and decides whether to trust it.

import { ApiRequestError, apiBaseUrl } from '@/services/api-client';

export interface DeviceCallState {
  deviceId: string;
  activeCall: boolean;
  observedAt: string;
}

const DEVICE_TIMEOUT_MS = 15_000;

function messageFrom(body: unknown): string | null {
  if (typeof body !== 'object' || body === null) return null;
  const message = (body as Record<string, unknown>).message;
  return typeof message === 'string' && message.length > 0 ? message : null;
}

function isAbortError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    (error as { name?: unknown }).name === 'AbortError'
  );
}

/**
 * Reports the current call state for a paired device. Requires the development
 * pairing token; an empty token is rejected by the backend with HTTP 401.
 */
export async function reportCallState(
  deviceId: string,
  activeCall: boolean,
  deviceToken: string,
): Promise<DeviceCallState> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DEVICE_TIMEOUT_MS);
  try {
    const res = await fetch(`${apiBaseUrl}/api/device-signals/call-state`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        'X-GuardPay-Device-Token': deviceToken,
      },
      body: JSON.stringify({
        deviceId,
        activeCall,
        observedAt: new Date().toISOString(),
      }),
    });
    if (!res.ok) {
      const body: unknown = await res.json().catch(() => undefined);
      throw new ApiRequestError(
        res.status,
        res.status === 401
          ? 'The device pairing token was rejected.'
          : (messageFrom(body) ?? `Device signal failed (${res.status})`),
      );
    }
    return (await res.json()) as DeviceCallState;
  } catch (error) {
    if (error instanceof ApiRequestError) throw error;
    if (isAbortError(error)) {
      throw new ApiRequestError(0, 'The device signal timed out. Please try again.');
    }
    throw new ApiRequestError(0, 'Unable to reach GuardPay. Check your connection and try again.');
  } finally {
    clearTimeout(timer);
  }
}
