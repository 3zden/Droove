import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Trip } from '../types/trips';
import { cancelTrip, completeTrip, createTrip, getTrip, markArrived, startTrip, TRIPS_API_URL } from './trips';

const mockTrip: Trip = {
  id: 't1',
  riderId: 'r1',
  driverId: null,
  status: 'REQUESTED',
  pickupLat: 33.57,
  pickupLng: -7.58,
  dropLat: 33.6,
  dropLng: -7.6,
  fareCents: 1500,
  surge: 1,
  requestedAt: '2026-07-29T10:00:00Z',
  matchedAt: null,
  startedAt: null,
  completedAt: null,
};

function mockFetchOnce(status: number, body: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('trips api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('createTrip() POSTs the pickup/drop coords and returns the created trip', async () => {
    mockFetchOnce(201, mockTrip);
    const result = await createTrip('jwt', { pickupLat: 33.57, pickupLng: -7.58, dropLat: 33.6, dropLng: -7.6 });
    expect(fetch).toHaveBeenCalledWith(
      `${TRIPS_API_URL}/trips`,
      expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ Authorization: 'Bearer jwt' }) }),
    );
    expect(result).toEqual(mockTrip);
  });

  it('getTrip() GETs a single trip by id', async () => {
    mockFetchOnce(200, mockTrip);
    const result = await getTrip('jwt', 't1');
    expect(fetch).toHaveBeenCalledWith(`${TRIPS_API_URL}/trips/t1`, expect.objectContaining({ method: 'GET' }));
    expect(result).toEqual(mockTrip);
  });

  it('cancelTrip() POSTs to the cancel endpoint', async () => {
    mockFetchOnce(200, { ...mockTrip, status: 'CANCELLED' });
    await cancelTrip('jwt', 't1');
    expect(fetch).toHaveBeenCalledWith(`${TRIPS_API_URL}/trips/t1/cancel`, expect.objectContaining({ method: 'POST' }));
  });

  it('markArrived()/startTrip()/completeTrip() POST to their respective endpoints', async () => {
    mockFetchOnce(200, mockTrip);
    await markArrived('jwt', 't1');
    expect(fetch).toHaveBeenCalledWith(`${TRIPS_API_URL}/trips/t1/arrived`, expect.objectContaining({ method: 'POST' }));

    mockFetchOnce(200, mockTrip);
    await startTrip('jwt', 't1');
    expect(fetch).toHaveBeenCalledWith(`${TRIPS_API_URL}/trips/t1/start`, expect.objectContaining({ method: 'POST' }));

    mockFetchOnce(200, mockTrip);
    await completeTrip('jwt', 't1');
    expect(fetch).toHaveBeenCalledWith(`${TRIPS_API_URL}/trips/t1/complete`, expect.objectContaining({ method: 'POST' }));
  });

  it('getTrip() throws a readable error on a non-2xx response', async () => {
    mockFetchOnce(404, { message: 'not found' });
    await expect(getTrip('jwt', 'missing')).rejects.toThrow();
  });
});
