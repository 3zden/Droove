package org.aezden.routingservice.Model;

import java.util.List;

/**
 * Our contract - deliberately not GraphHopper's.
 *
 * <p>Base SI units so callers round once, at their own final value. Pricing multiplies
 * distance by a cents-per-km rate; rounding to kilometres here would inject error into
 * money for nothing.
 *
 * <p>{@code points} is [lat, lng] pairs, Leaflet's order. GraphHopper returns GeoJSON,
 * which is [lng, lat] - the swap happens once, here, so no consumer has to know.
 *
 * <p>{@code source} exists so a degraded answer never looks identical to a good one.
 */
public record RouteResponse(
        double distanceMeters,
        long durationSeconds,
        RouteSource source,
        List<double[]> points
) {
    public enum RouteSource {
        /** Real road route from the routing engine. */
        ROAD,
        /** Straight line with a detour factor - the engine was unreachable or had no route. */
        FALLBACK
    }
}
