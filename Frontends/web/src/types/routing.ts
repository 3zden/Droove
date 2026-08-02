export interface RouteRequest {
  fromLat: number;
  fromLng: number;
  toLat: number;
  toLng: number;
}

/**
 * ROAD  - a real route from the routing engine.
 * FALLBACK - the engine could not answer, so this is a straight-line estimate.
 *
 * The distinction is deliberately visible: a degraded answer that looks identical
 * to a good one is a bug nobody ever finds.
 */
export type RouteSource = 'ROAD' | 'FALLBACK';

export interface Route {
  distanceMeters: number;
  durationSeconds: number;
  source: RouteSource;
  /** [lat, lng] pairs, ready for Leaflet. The service already swapped GeoJSON's order. */
  points: [number, number][];
}
