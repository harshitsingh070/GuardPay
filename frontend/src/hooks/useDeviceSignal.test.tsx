import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { createDeviceId, isUuid, useDeviceSignal } from '@/hooks/useDeviceSignal';

describe('useDeviceSignal', () => {
  beforeEach(() => {
    window.localStorage.clear();
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
});
