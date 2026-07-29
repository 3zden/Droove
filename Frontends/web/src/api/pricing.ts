import type { Quote, QuoteRequest } from '../types/pricing';

// pricing-service is unprefixed at its own port until the gateway (M5) exists -
// see BRIEFING-FRONTEND.md.
export const PRICING_API_URL = import.meta.env.VITE_PRICING_API_URL ?? 'http://localhost:8104';

// EXERCISE (see BRIEFING-FRONTEND.md): GET `${PRICING_API_URL}/quote?pickupLat=...&pickupLng=...&dropLat=...&dropLng=...`.
export async function getQuote(_params: QuoteRequest): Promise<Quote> {
  throw new Error('getQuote() is not wired up yet - see BRIEFING-FRONTEND.md');
}
