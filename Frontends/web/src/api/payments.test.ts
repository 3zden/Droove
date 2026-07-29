import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LedgerTransaction, WalletBalance } from '../types/payments';
import { getTripLedger, getWallet, PAYMENTS_API_URL, topUp } from './payments';

const mockWallet: WalletBalance = { balanceCents: 5000 };
const mockLedger: LedgerTransaction[] = [
  { id: 'tx1', type: 'DISBURSE', tripId: 't1', createdAt: '2026-07-29T10:00:00Z', amountCents: 1200 },
];

function mockFetchOnce(status: number, body: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('payments api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('getWallet() GETs the wallet balance', async () => {
    mockFetchOnce(200, mockWallet);
    const result = await getWallet('jwt');
    expect(fetch).toHaveBeenCalledWith(`${PAYMENTS_API_URL}/wallet`, expect.objectContaining({ method: 'GET' }));
    expect(result).toEqual(mockWallet);
  });

  it('topUp() POSTs the amount and returns the updated balance', async () => {
    mockFetchOnce(200, mockWallet);
    const result = await topUp('jwt', 2000);
    expect(fetch).toHaveBeenCalledWith(
      `${PAYMENTS_API_URL}/wallet/topup`,
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ amountCents: 2000 }) }),
    );
    expect(result).toEqual(mockWallet);
  });

  it('getTripLedger() GETs the ledger for a trip', async () => {
    mockFetchOnce(200, mockLedger);
    const result = await getTripLedger('jwt', 't1');
    expect(fetch).toHaveBeenCalledWith(`${PAYMENTS_API_URL}/ledger/trip/t1`, expect.objectContaining({ method: 'GET' }));
    expect(result).toEqual(mockLedger);
  });
});
