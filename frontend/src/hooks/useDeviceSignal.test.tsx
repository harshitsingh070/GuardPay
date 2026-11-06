import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createDeviceId, isUuid, useDeviceSignal } from '@/hooks/useDeviceSignal';
import { ApiRequestError } from '@/services/api-client';
import { registerDevice, rotateDevice } from '@/services/device-service';
import type { GuardPayNativeBridge } from '@/services/native-bridge';

vi.mock('@/services/device-service', () => ({
  registerDevice: vi.fn(),
  rotateDevice: vi.fn(),
  reportCallState: vi.fn(),
}));

describe('useDeviceSignal', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    delete window.GuardPayNative;
  });

  it('creates a v4 UUID that the backend accepts', () => {
    const deviceId = createDeviceId();

    expect(isUuid(deviceId)).toBe(true);
    expect(deviceId[14]).toBe('4');
    expect(['8', '9', 'a', 'b']).toContain(deviceId[19]);
  });

  it('replaces a previously stored non-UUID device id', () => {
    window.localStorage.setItem('guardpay.device-id', 'gp-device-123');

    const { result } = renderHook(() => useDeviceSignal());

    expect(isUuid(result.current.deviceId)).toBe(true);
    expect(window.localStorage.getItem('guardpay.device-id')).toBe(result.current.deviceId);
  });

  it('reuses a valid stored device id', () => {
    const stored = createDeviceId();
    window.localStorage.setItem('guardpay.device-id', stored);

    const { result } = renderHook(() => useDeviceSignal());

    expect(result.current.deviceId).toBe(stored);
  });

  it('rotates a migrated device credential and shares it with the native shell', async () => {
    const stored = createDeviceId();
    const setDeviceToken = vi.fn();
    const bridge: GuardPayNativeBridge = {
      getDeviceId: () => stored,
      getApiBaseUrl: () => 'http://localhost:8080',
      isCallStateKnown: () => false,
      isCallActive: () => false,
      getLastObservedAt: () => '',
      setDeviceToken,
      openUrl: () => undefined,
    };
    window.GuardPayNative = bridge;
    vi.mocked(registerDevice).mockRejectedValue(
      new ApiRequestError(409, 'This device is already registered'),
    );
    vi.mocked(rotateDevice).mockResolvedValue({
      deviceId: stored,
      deviceToken: 'device-credential',
      label: 'GuardPay device',
      createdAt: '2026-11-05T00:00:00Z',
    });

    const { result } = renderHook(() => useDeviceSignal({ autoProvision: true }));

    await waitFor(() => expect(result.current.token).toBe('device-credential'));
    expect(rotateDevice).toHaveBeenCalledWith(stored);
    expect(setDeviceToken).toHaveBeenCalledWith('device-credential');
    expect(window.localStorage.getItem('guardpay.device-token')).toBe('device-credential');
  });
});
