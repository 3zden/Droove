import type { Quote, QuoteRequest } from '../types/pricing';

// Relative by default: the browser always talks to one origin, and the thing in front
// routes to pricing-service - the Vite dev proxy now (vite.config.ts), the gateway later.
// Same-origin means no CORS, and no dev-only annotations in the Java service.
export const PRICING_API_URL = import.meta.env.VITE_PRICING_API_URL ?? '/api/pricing';

export async function getQuote(params: QuoteRequest): Promise<Quote> {
  const query = new URLSearchParams({
    pickupLat: String(params.pickupLat),
    pickupLng: String(params.pickupLng),
    dropLat: String(params.dropLat),
    dropLng: String(params.dropLng),
  });

  const response = await fetch(`${PRICING_API_URL}/quote?${query}`, { method: 'GET' });
  if (!response.ok) {
    throw new Error(`Could not get a quote (${response.status})`);
  }
  return (await response.json()) as Quote;
}
