import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, setCsrfToken } from '@/services/api-client';

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

describe('apiFetch CSRF handling', () => {
  afterEach(() => {
    setCsrfToken(null);
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('bootstraps the anonymous token and echoes it on a mutation', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ csrfToken: 'csrf-anonymous' }))
      .mockResolvedValueOnce(
        jsonResponse({ authenticated: false, user: null, csrfToken: 'csrf-rotated' }),
      );
    vi.stubGlobal('fetch', fetchMock);
    setCsrfToken(null);

    await apiFetch('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: 'sam@example.com', password: 'correct-horse' }),
    });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]?.[0]).toContain('/api/auth/csrf');
    const headers = fetchMock.mock.calls[1]?.[1]?.headers as Record<string, string>;
    expect(headers['X-CSRF-Token']).toBe('csrf-anonymous');
  });

  it('refreshes a stale token once and retries the rejected mutation', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ message: 'Invalid CSRF token' }, 403))
      .mockResolvedValueOnce(jsonResponse({ csrfToken: 'csrf-fresh' }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);
    setCsrfToken('csrf-stale');

    await apiFetch('/api/transactions', { method: 'DELETE' });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    const first = fetchMock.mock.calls[0]?.[1]?.headers as Record<string, string>;
    const retried = fetchMock.mock.calls[2]?.[1]?.headers as Record<string, string>;
    expect(first['X-CSRF-Token']).toBe('csrf-stale');
    expect(retried['X-CSRF-Token']).toBe('csrf-fresh');
  });

  it('returns no body for a 204 response', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error('no body');
      },
    } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);
    setCsrfToken('csrf-token');

    await expect(apiFetch<void>('/api/devices/device-1', { method: 'DELETE' })).resolves.toBe(
      undefined,
    );
  });
});
