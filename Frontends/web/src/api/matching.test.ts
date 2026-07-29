import { beforeEach, describe, expect, it, vi } from 'vitest';
import { acceptOffer, declineOffer, MATCHING_API_URL } from './matching';

function mockFetchOnce(status: number, body: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('matching api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('acceptOffer() POSTs to the accept endpoint', async () => {
    mockFetchOnce(200, { status: 'ACCEPTED' });
    await acceptOffer('jwt', 'offer1');
    expect(fetch).toHaveBeenCalledWith(`${MATCHING_API_URL}/offers/offer1/accept`, expect.objectContaining({ method: 'POST' }));
  });

  it('declineOffer() POSTs to the decline endpoint', async () => {
    mockFetchOnce(200, { status: 'DECLINED' });
    await declineOffer('jwt', 'offer1');
    expect(fetch).toHaveBeenCalledWith(`${MATCHING_API_URL}/offers/offer1/decline`, expect.objectContaining({ method: 'POST' }));
  });
});
