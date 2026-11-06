/**
 * Bridge exposed by the Android WebView shell (`window.GuardPayNative`).
 * On the plain web build this is absent and every helper degrades safely.
 */
export interface GuardPayNativeBridge {
  getDeviceId(): string;
  getApiBaseUrl(): string;
  isCallStateKnown(): boolean;
  isCallActive(): boolean;
  getLastObservedAt(): string;
  setDeviceToken?(token: string): void;
  openUrl(url: string): void;
}

declare global {
  interface Window {
    GuardPayNative?: GuardPayNativeBridge;
    __guardPayNativeCallState?: (activeCall: boolean, observedAt: string) => void;
  }
}

export function getNativeBridge(): GuardPayNativeBridge | null {
  if (typeof window === 'undefined') return null;
  try {
    const bridge = window.GuardPayNative;
    return bridge !== undefined && typeof bridge.getDeviceId === 'function' ? bridge : null;
  } catch {
    return null;
  }
}

export function readNative<T>(read: (bridge: GuardPayNativeBridge) => T, fallback: T): T {
  const bridge = getNativeBridge();
  if (bridge === null) return fallback;
  try {
    return read(bridge);
  } catch {
    return fallback;
  }
}

/** Opens PayPal (or any external page) in the device browser instead of this WebView. */
export function openExternalUrl(url: string): boolean {
  const bridge = getNativeBridge();
  if (bridge === null) return false;
  try {
    bridge.openUrl(url);
    return true;
  } catch {
    return false;
  }
}
