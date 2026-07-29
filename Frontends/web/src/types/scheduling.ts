export type BookingStatus = 'SCHEDULED' | 'TRIGGERED' | 'CANCELLED';

export interface CreateBookingRequest {
  pickupTime: string;
  pickupLat: number;
  pickupLng: number;
  dropLat: number;
  dropLng: number;
}

export interface Booking {
  id: string;
  pickupTime: string;
  pickupLat: number;
  pickupLng: number;
  dropLat: number;
  dropLng: number;
  status: BookingStatus;
  tripId: string | null;
}
