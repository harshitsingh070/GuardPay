// Device registration and call-state intake. The browser cannot read real
// telephony state, so the UI reports a user-confirmed signal; the backend
// authenticates each per-device credential and stores it for the owner.

import { ApiRequestError, apiBaseUrl, apiFetch } from '@/services/api-client';

export interface DeviceCallState {
  deviceId: string;
  activeCall: boolean;
  observedAt: string;
}

export interface DeviceSummary {
  deviceId: string;
  label: string | null;
  createdAt: string;
  lastSeenAt: string | null;
  revokedAt: string | null;
}

export interface DeviceRegistration {
  deviceId: string;
  deviceToken: string;
  label: string | null;
  createdAt: string;
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

/** Devices owned by the signed-in account; credentials are never returned here. */
export function listDevices(): Promise<DeviceSummary[]> {
  return apiFetch<DeviceSummary[]>('/api/devices');
}

/** Registers a device id under the signed-in account and returns its credential once. */
export function registerDevice(deviceId: string, label?: string): Promise<DeviceRegistration> {
  return apiFetch<DeviceRegistration>('/api/devices', {
    method: 'POST',
    body: JSON.stringify({ deviceId, label }),
  });
}

/** Replaces a device credential after the original was lost or suspected leaked. */
export function rotateDevice(deviceId: string): Promise<DeviceRegistration> {
  return apiFetch<DeviceRegistration>(`/api/devices/${encodeURIComponent(deviceId)}/rotate`, {
    method: 'POST',
  });
}

/** Revokes a device and clears its stored call state. */
export function revokeDevice(deviceId: string): Promise<void> {
  return apiFetch<void>(`/api/devices/${encodeURIComponent(deviceId)}`, {
    method: 'DELETE',
  });
}

/**
 * Reports the current call state for a registered device. The per-device
 * credential is the only authentication this endpoint accepts.
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
          ? 'The device credential was rejected. Register this device again.'
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
