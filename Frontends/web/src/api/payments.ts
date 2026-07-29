import type { LedgerTransaction, WalletBalance } from '../types/payments';

// payment-service is unprefixed at its own port until the gateway (M5) exists -
// see BRIEFING-FRONTEND.md.
export const PAYMENTS_API_URL = import.meta.env.VITE_PAYMENTS_API_URL ?? 'http://localhost:8105';

// EXERCISE (see BRIEFING-FRONTEND.md): GET `${PAYMENTS_API_URL}/wallet`, needs the auth token.
export async function getWallet(_token: string): Promise<WalletBalance> {
  throw new Error('getWallet() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: POST `${PAYMENTS_API_URL}/wallet/topup` with `{ amountCents }`.
export async function topUp(_token: string, _amountCents: number): Promise<WalletBalance> {
  throw new Error('topUp() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: GET `${PAYMENTS_API_URL}/ledger/trip/{tripId}`.
export async function getTripLedger(_token: string, _tripId: string): Promise<LedgerTransaction[]> {
  throw new Error('getTripLedger() is not wired up yet - see BRIEFING-FRONTEND.md');
}
