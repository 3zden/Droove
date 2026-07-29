import type { Booking, CreateBookingRequest } from '../types/scheduling';

// scheduling-service is unprefixed at its own port until the gateway (M5) exists -
// see BRIEFING-FRONTEND.md.
export const SCHEDULING_API_URL = import.meta.env.VITE_SCHEDULING_API_URL ?? 'http://localhost:8106';

// EXERCISE (see BRIEFING-FRONTEND.md): POST `${SCHEDULING_API_URL}/bookings`.
export async function createBooking(_token: string, _payload: CreateBookingRequest): Promise<Booking> {
  throw new Error('createBooking() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: GET `${SCHEDULING_API_URL}/bookings/mine`.
export async function getMyBookings(_token: string): Promise<Booking[]> {
  throw new Error('getMyBookings() is not wired up yet - see BRIEFING-FRONTEND.md');
}

// EXERCISE: POST `${SCHEDULING_API_URL}/bookings/{id}/cancel`.
export async function cancelBooking(_token: string, _bookingId: string): Promise<void> {
  throw new Error('cancelBooking() is not wired up yet - see BRIEFING-FRONTEND.md');
}
