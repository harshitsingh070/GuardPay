import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiRequestError } from '@/services/api-client';
import {
  registerDevice,
  reportCallState,
  rotateDevice,
} from '@/services/device-service';
import { getDeviceFingerprint } from '@/services/device-fingerprint';
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
  return stored === null ? '' : stored.trim();
}

export interface DeviceSignal {
  deviceId: string;
  fingerprint: string;
  token: string;
  activeCall: boolean | null;
  observedAt: string | null;
  reporting: boolean;
  provisioning: boolean;
  error: string | null;
  /** True when the Android shell detects calls automatically. */
  automatic: boolean;
  /** Changes whenever a new call-state observation is stored. */
  stateKey: string | null;
  setToken: (token: string) => void;
  provision: () => Promise<string | null>;
  report: (activeCall: boolean) => Promise<void>;
}

interface UseDeviceSignalOptions {
  /** Registers this device after login. Leave off in unit tests and public views. */
  autoProvision?: boolean;
}

/**
 * Per-device call-state signalling. A browser cannot read the phone's
 * telephony state, so the user reports the state explicitly; the backend
 * authenticates the per-device credential and the risk engine uses it only
 * while the observation is fresh.
 */
export function useDeviceSignal({
  autoProvision = false,
}: UseDeviceSignalOptions = {}): DeviceSignal {
  const [native] = useState(() => getNativeBridge());
  const [deviceId, setDeviceId] = useState<string>(initialDeviceId);
  const [fingerprint] = useState<string>(getDeviceFingerprint);
  const [token, setTokenState] = useState<string>(initialToken);
  const [activeCall, setActiveCall] = useState<boolean | null>(null);
  const [observedAt, setObservedAt] = useState<string | null>(null);
  const [reporting, setReporting] = useState(false);
  const [provisioning, setProvisioning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const provisioningRequest = useRef<Promise<string | null> | null>(null);

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

  const syncNativeToken = useCallback((next: string) => {
    const bridge = getNativeBridge();
    if (bridge === null || typeof bridge.setDeviceToken !== 'function') return;
    try {
      bridge.setDeviceToken(next);
    } catch {
      // The native shell may be tearing down; the browser token still works.
    }
  }, []);

  const setToken = useCallback(
    (next: string) => {
      const trimmed = next.trim();
      setTokenState(trimmed);
      store(DEVICE_TOKEN_KEY, trimmed);
      syncNativeToken(trimmed);
    },
    [syncNativeToken],
  );

  const provision = useCallback((): Promise<string | null> => {
    const existing = token.trim();
    if (existing !== '') return Promise.resolve(existing);
    if (provisioningRequest.current !== null) return provisioningRequest.current;

    const request = (async () => {
      setProvisioning(true);
      setError(null);
      try {
        let issued;
        try {
          issued = await registerDevice(deviceId, 'GuardPay device');
        } catch (failure) {
          // A device migrated from historical transactions already has an owner.
          // The signed-in owner may rotate that unrecoverable credential.
          if (failure instanceof ApiRequestError && failure.status === 409) {
            issued = await rotateDevice(deviceId);
          } else {
            throw failure;
          }
        }
        setToken(issued.deviceToken);
        return issued.deviceToken;
      } catch (failure) {
        if (!(failure instanceof ApiRequestError && failure.status === 401)) {
          setError(
            failure instanceof ApiRequestError
              ? failure.message
              : 'Unable to register this device.',
          );
        }
        return null;
      } finally {
        provisioningRequest.current = null;
        setProvisioning(false);
      }
    })();

    provisioningRequest.current = request;
    return request;
  }, [deviceId, setToken, token]);

  useEffect(() => {
    // Only the Android shell needs a backend credential: it reports call state
    // from native code. A plain browser has no telephony signal to report.
    if (autoProvision && native !== null) void provision();
  }, [autoProvision, native, provision]);

  const report = useCallback(
    async (nextActive: boolean) => {
      if (reporting) return;
      setReporting(true);
      setError(null);
      try {
        let credential = token.trim();
        if (credential === '') {
          credential = (await provision()) ?? '';
        }
        if (credential === '') {
          setError('This device is not registered yet. Sign in and try again.');
          return;
        }
        const state = await reportCallState(deviceId, nextActive, credential);
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
    [deviceId, provision, reporting, token],
  );

  return {
    deviceId,
    fingerprint,
    token,
    activeCall,
    observedAt,
    reporting,
    provisioning,
    error,
    automatic: native !== null,
    stateKey: observedAt === null || activeCall === null ? null : `${activeCall}:${observedAt}`,
    setToken,
    provision,
    report,
  };
}
