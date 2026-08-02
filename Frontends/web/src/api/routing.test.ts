import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Route } from '../types/routing';
import { getRoute, ROUTING_API_URL } from './routing';

const mockRoute: Route = {
  distanceMeters: 8400,
  durationSeconds: 1008,
  source: 'ROAD',
  points: [
    [33.57, -7.58],
    [33.6, -7.6],
  ],
};

function mockFetchOnce(status: number, body: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('routing api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('getRoute() GETs the route endpoint with from/to as query params', async () => {
    mockFetchOnce(200, mockRoute);
    const result = await getRoute({ fromLat: 33.57, fromLng: -7.58, toLat: 33.6, toLng: -7.6 });
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(calledUrl.startsWith(`${ROUTING_API_URL}/route?`)).toBe(true);
    expect(calledUrl).toContain('fromLat=33.57');
    expect(calledUrl).toContain('toLng=-7.6');
    expect(result).toEqual(mockRoute);
  });

  it('getRoute() throws a readable error on a non-2xx response', async () => {
    mockFetchOnce(400, {});
    await expect(getRoute({ fromLat: 500, fromLng: 0, toLat: 0, toLng: 0 })).rejects.toThrow();
  });
});
