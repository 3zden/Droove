// matching-service is unprefixed at its own port until the gateway (M5) exists -
// see BRIEFING-FRONTEND.md.
export const MATCHING_API_URL = import.meta.env.VITE_MATCHING_API_URL ?? 'http://localhost:8103';

// EXERCISE (see BRIEFING-FRONTEND.md): POST `${MATCHING_API_URL}/offers/{offerId}/accept`.
export async function acceptOffer(_token: string, _offerId: string): Promise<void> {
  throw new Error('acceptOffer() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: POST `${MATCHING_API_URL}/offers/{offerId}/decline`.
export async function declineOffer(_token: string, _offerId: string): Promise<void> {
  throw new Error('declineOffer() is not wired up yet - see BRIEFING-FRONTEND.md');
}
