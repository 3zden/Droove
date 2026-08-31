import type { LedgerTransaction, WalletBalance } from '../types/payments';

// payment-service is unprefixed at its own port until the gateway (M5) exists -
// see BRIEFING-FRONTEND.md.
export const PAYMENTS_API_URL = import.meta.env.VITE_PAYMENTS_API_URL ?? 'http://localhost:8105';

function authHeaders(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

async function request<T>(token: string, input: RequestInfo | URL, init: RequestInit = {}): Promise<T> {
  const response = await fetch(input, {
    ...init,
    headers: { ...authHeaders(token), 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    throw new Error(`Payment request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function getWallet(token: string): Promise<WalletBalance> {
  return request<WalletBalance>(token, `${PAYMENTS_API_URL}/wallet`, { method: 'GET' });
}

export function topUp(token: string, amountCents: number): Promise<WalletBalance> {
  return request<WalletBalance>(token, `${PAYMENTS_API_URL}/wallet/topup`, {
    method: 'POST',
    body: JSON.stringify({ amountCents }),
  });
}

export function getTripLedger(token: string, tripId: string): Promise<LedgerTransaction[]> {
  return request<LedgerTransaction[]>(token, `${PAYMENTS_API_URL}/ledger/trip/${encodeURIComponent(tripId)}`, {
    method: 'GET',
  });
}
