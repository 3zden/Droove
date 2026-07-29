export interface QuoteRequest {
  pickupLat: number;
  pickupLng: number;
  dropLat: number;
  dropLng: number;
}

export interface Quote {
  fareCents: number;
  surge: number;
  distanceKm: number;
  durationMin: number;
}
