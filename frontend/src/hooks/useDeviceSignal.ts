import { useCallback, useEffect, useState } from 'react';
import { ApiRequestError } from '@/services/api-client';
import { reportCallState } from '@/services/device-service';
import { getNativeBridge, readNative } from '@/services/native-bridge';

const DEVICE_ID_KEY = 'guardpay.device-id';
const DEVICE_TOKEN_KEY = 'guardpay.device-token';

function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function store(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Private mode / quota: the signal still works for this session.
  }
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value);
}

/**
 * Generates a v4 UUID. `crypto.randomUUID()` only exists in secure contexts,
 * and the phone demo runs over plain HTTP on the LAN, so this uses
 * `getRandomValues` (available everywhere) and falls back to Math.random.
 * The backend's device id is a UUID, so a non-UUID id would be rejected 400.
 */
export function createDeviceId(): string {
  let bytes: Uint8Array | null = null;
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
      bytes = crypto.getRandomValues(new Uint8Array(16));
    }
  } catch {
    bytes = null;
  }

  if (bytes === null) {
    bytes = new Uint8Array(16);
    for (let i = 0; i < bytes.length; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }

  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function initialDeviceId(): string {
  // The Android shell owns the device identity so its native call reports and
  // the browser's guarded-payment request refer to the same device.
  const nativeId = readNative((bridge) => bridge.getDeviceId().trim(), '');
  if (isUuid(nativeId)) {
    store(DEVICE_ID_KEY, nativeId);
    return nativeId;
  }
  const stored = readStored(DEVICE_ID_KEY);
  if (stored !== null && isUuid(stored)) return stored;
  const fresh = createDeviceId();
  store(DEVICE_ID_KEY, fresh);
  return fresh;
}

function initialToken(): string {
  const stored = readStored(DEVICE_TOKEN_KEY);
  if (stored !== null && stored.trim() !== '') return stored;
  return import.meta.env.VITE_GUARDPAY_DEVICE_TOKEN ?? '';
}

export interface DeviceSignal {
  deviceId: string;
  token: string;
  activeCall: boolean | null;
  observedAt: string | null;
  reporting: boolean;
  error: string | null;
  /** True when the Android shell detects calls automatically. */
  automatic: boolean;
  /** Changes whenever a new call-state observation is stored. */
  stateKey: string | null;
  setToken: (token: string) => void;
  report: (activeCall: boolean) => Promise<void>;
}

/**
 * Development call-state signalling. A browser cannot read the phone's
 * telephony state, so the user reports the state explicitly; the backend
 * stores it (pairing token required) and the risk engine uses it only while
 * the observation is fresh.
 */
export function useDeviceSignal(): DeviceSignal {
  const [native] = useState(() => getNativeBridge());
  const [deviceId, setDeviceId] = useState<string>(initialDeviceId);
  const [token, setTokenState] = useState<string>(initialToken);
  const [activeCall, setActiveCall] = useState<boolean | null>(null);
  const [observedAt, setObservedAt] = useState<string | null>(null);
  const [reporting, setReporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Repairs a non-UUID id kept from an older build (or a hot reload), which
  // the backend would reject with 400.
  useEffect(() => {
    if (isUuid(deviceId)) return;
    const fresh = createDeviceId();
    store(DEVICE_ID_KEY, fresh);
    setDeviceId(fresh);
  }, [deviceId]);

  // Android shell: native telephony callbacks update the displayed state, and
  // the shell itself posts the observation to the backend (same device id).
  useEffect(() => {
    if (native === null) return;
    const applyNativeState = (nextActive: boolean, at: string) => {
      setActiveCall(nextActive);
      setObservedAt(at);
      setError(null);
    };
    window.__guardPayNativeCallState = applyNativeState;
    try {
      if (native.isCallStateKnown()) {
        setActiveCall(native.isCallActive());
        const observed = native.getLastObservedAt();
        if (observed !== '') setObservedAt(observed);
      }
    } catch {
      // The bridge is optional; keep the manual fallback state.
    }
    return () => {
      if (window.__guardPayNativeCallState === applyNativeState) {
        delete window.__guardPayNativeCallState;
      }
    };
  }, [native]);

  const setToken = useCallback((next: string) => {
    const trimmed = next.trim();
    setTokenState(trimmed);
    store(DEVICE_TOKEN_KEY, trimmed);
  }, []);

  const report = useCallback(
    async (nextActive: boolean) => {
      if (reporting) return;
      if (token.trim() === '') {
        setError('Add the device pairing token first.');
        return;
      }
      setReporting(true);
      setError(null);
      try {
        const state = await reportCallState(deviceId, nextActive, token);
        setActiveCall(state.activeCall);
        setObservedAt(state.observedAt);
      } catch (failure) {
        setError(
          failure instanceof ApiRequestError
            ? failure.message
            : 'Unable to report the phone call state.',
        );
      } finally {
        setReporting(false);
      }
    },
    [deviceId, reporting, token],
  );

  return {
    deviceId,
    token,
    activeCall,
    observedAt,
    reporting,
    error,
    automatic: native !== null,
    stateKey: observedAt === null || activeCall === null ? null : `${activeCall}:${observedAt}`,
    setToken,
    report,
  };
}
