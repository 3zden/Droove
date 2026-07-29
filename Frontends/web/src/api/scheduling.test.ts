import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Booking } from '../types/scheduling';
import { cancelBooking, createBooking, getMyBookings, SCHEDULING_API_URL } from './scheduling';

const mockBooking: Booking = {
  id: 'b1',
  pickupTime: '2026-07-30T09:00:00Z',
  pickupLat: 33.57,
  pickupLng: -7.58,
  dropLat: 33.6,
  dropLng: -7.6,
  status: 'SCHEDULED',
  tripId: null,
};

function mockFetchOnce(status: number, body: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('scheduling api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('createBooking() POSTs the booking payload', async () => {
    mockFetchOnce(201, mockBooking);
    const result = await createBooking('jwt', {
      pickupTime: mockBooking.pickupTime,
      pickupLat: mockBooking.pickupLat,
      pickupLng: mockBooking.pickupLng,
      dropLat: mockBooking.dropLat,
      dropLng: mockBooking.dropLng,
    });
    expect(fetch).toHaveBeenCalledWith(`${SCHEDULING_API_URL}/bookings`, expect.objectContaining({ method: 'POST' }));
    expect(result).toEqual(mockBooking);
  });

  it('getMyBookings() GETs the caller\'s bookings', async () => {
    mockFetchOnce(200, [mockBooking]);
    const result = await getMyBookings('jwt');
    expect(fetch).toHaveBeenCalledWith(`${SCHEDULING_API_URL}/bookings/mine`, expect.objectContaining({ method: 'GET' }));
    expect(result).toEqual([mockBooking]);
  });

  it('cancelBooking() POSTs to the cancel endpoint', async () => {
    mockFetchOnce(200, {});
    await cancelBooking('jwt', 'b1');
    expect(fetch).toHaveBeenCalledWith(`${SCHEDULING_API_URL}/bookings/b1/cancel`, expect.objectContaining({ method: 'POST' }));
  });
});
