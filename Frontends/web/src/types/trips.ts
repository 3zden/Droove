export type TripStatus =
  | 'REQUESTED'
  | 'MATCHED'
  | 'DRIVER_ARRIVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_DRIVERS_FOUND';

export interface CreateTripRequest {
  pickupLat: number;
  pickupLng: number;
  dropLat: number;
  dropLng: number;
}

export interface Trip {
  id: string;
  riderId: string;
  driverId: string | null;
  status: TripStatus;
  pickupLat: number;
  pickupLng: number;
  dropLat: number;
  dropLng: number;
  fareCents: number;
  surge: number;
  requestedAt: string;
  matchedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
}
