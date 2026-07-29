import type { CreateTripRequest, Trip } from '../types/trips';

// trip-service is unprefixed at its own port until the gateway (M5) exists -
// see BRIEFING-FRONTEND.md. Confirmed against trip-service-rewrite.md: its own
// routes are bare (`/trips`, not `/api/trips` - the gateway adds that prefix).
export const TRIPS_API_URL = import.meta.env.VITE_TRIPS_API_URL ?? 'http://localhost:8102';

// EXERCISE (see BRIEFING-FRONTEND.md): POST `${TRIPS_API_URL}/trips`.
export async function createTrip(_token: string, _payload: CreateTripRequest): Promise<Trip> {
  throw new Error('createTrip() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: GET `${TRIPS_API_URL}/trips/{id}`.
export async function getTrip(_token: string, _tripId: string): Promise<Trip> {
  throw new Error('getTrip() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: POST `${TRIPS_API_URL}/trips/{id}/cancel`.
export async function cancelTrip(_token: string, _tripId: string): Promise<Trip> {
  throw new Error('cancelTrip() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: POST `${TRIPS_API_URL}/trips/{id}/arrived` - driver only.
export async function markArrived(_token: string, _tripId: string): Promise<Trip> {
  throw new Error('markArrived() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: POST `${TRIPS_API_URL}/trips/{id}/start` - driver only.
export async function startTrip(_token: string, _tripId: string): Promise<Trip> {
  throw new Error('startTrip() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: POST `${TRIPS_API_URL}/trips/{id}/complete` - driver only.
export async function completeTrip(_token: string, _tripId: string): Promise<Trip> {
  throw new Error('completeTrip() is not wired up yet - see BRIEFING-FRONTEND.md');
}
