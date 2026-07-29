import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Quote } from '../types/pricing';
import { getQuote, PRICING_API_URL } from './pricing';

const mockQuote: Quote = { fareCents: 1840, surge: 1.2, distanceKm: 8.4, durationMin: 16.8 };

function mockFetchOnce(status: number, body: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('pricing api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('getQuote() GETs the quote endpoint with pickup/drop as query params', async () => {
    mockFetchOnce(200, mockQuote);
    const result = await getQuote({ pickupLat: 33.57, pickupLng: -7.58, dropLat: 33.6, dropLng: -7.6 });
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(calledUrl.startsWith(`${PRICING_API_URL}/quote?`)).toBe(true);
    expect(calledUrl).toContain('pickupLat=33.57');
    expect(calledUrl).toContain('dropLng=-7.6');
    expect(result).toEqual(mockQuote);
  });

  it('getQuote() throws a readable error on a non-2xx response', async () => {
    mockFetchOnce(500, {});
    await expect(getQuote({ pickupLat: 0, pickupLng: 0, dropLat: 0, dropLng: 0 })).rejects.toThrow();
  });
});
