import type { Route, RouteRequest } from '../types/routing';

// Relative by default - routed by the Vite dev proxy now, the gateway later.
export const ROUTING_API_URL = import.meta.env.VITE_ROUTING_API_URL ?? '/api/routing';

export async function getRoute(params: RouteRequest): Promise<Route> {
  const query = new URLSearchParams({
    fromLat: String(params.fromLat),
    fromLng: String(params.fromLng),
    toLat: String(params.toLat),
    toLng: String(params.toLng),
  });

  const response = await fetch(`${ROUTING_API_URL}/route?${query}`, { method: 'GET' });
  if (!response.ok) {
    throw new Error(`Could not load the route (${response.status})`);
  }
  return (await response.json()) as Route;
}
