package org.aezden.routingservice.Services;

import org.aezden.routingservice.Clients.GraphHopperClient;
import org.aezden.routingservice.Model.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * Urban circuity factor: real road distance is typically 1.2-1.4x the straight line.
     * Only used when the engine cannot answer.
     */
    private static final double DETOUR_FACTOR = 1.3;

    /** Fallback speed, matching the flat estimate pricing used before routing existed. */
    private static final double FALLBACK_SPEED_KMH = 30.0;

    private final GraphHopperClient graphHopper;

    public RoutingService(GraphHopperClient graphHopper) {
        this.graphHopper = graphHopper;
    }

    public RouteResponse getRoute(double fromLat, double fromLng, double toLat, double toLng) {
        validate(fromLat, fromLng, "pickup");
        validate(toLat, toLng, "drop-off");

        try {
            GraphHopperClient.GhPath path = graphHopper.route(fromLat, fromLng, toLat, toLng);
            if (path != null) {
                return new RouteResponse(
                        path.distance(),
                        path.time() / 1000,               // GraphHopper answers in milliseconds
                        RouteResponse.RouteSource.ROAD,
                        toLatLng(path.points()));
            }
            log.warn("Routing engine returned no path for {},{} -> {},{}; falling back",
                    fromLat, fromLng, toLat, toLng);
        } catch (Exception e) {
            // Degrade, never fail: a straight-line estimate still lets the rider see a
            // price. Refusing to answer because a routing container is restarting is the
            // worse outcome. The caller can tell the difference from `source`.
            log.warn("Routing engine unavailable ({}); falling back to straight line", e.toString());
        }

        return fallback(fromLat, fromLng, toLat, toLng);
    }

    private RouteResponse fallback(double fromLat, double fromLng, double toLat, double toLng) {
        double distanceMeters = haversineMeters(fromLat, fromLng, toLat, toLng) * DETOUR_FACTOR;
        long durationSeconds = Math.round(distanceMeters / (FALLBACK_SPEED_KMH * 1000 / 3600));
        // Two points: the map draws a straight line, which honestly reflects what we know.
        List<double[]> points = List.of(
                new double[]{fromLat, fromLng},
                new double[]{toLat, toLng});
        return new RouteResponse(distanceMeters, durationSeconds, RouteResponse.RouteSource.FALLBACK, points);
    }

    /** GeoJSON gives [lng, lat]; Leaflet wants [lat, lng]. Swap once, here. */
    private List<double[]> toLatLng(GraphHopperClient.GhPoints points) {
        if (points == null || points.coordinates() == null) {
            return List.of();
        }
        List<double[]> result = new ArrayList<>(points.coordinates().size());
        for (List<Double> coordinate : points.coordinates()) {
            if (coordinate.size() >= 2) {
                result.add(new double[]{coordinate.get(1), coordinate.get(0)});
            }
        }
        return result;
    }

    /**
     * Duplicated from pricing-service on purpose. Ten lines of arithmetic is a smaller
     * cost than a shared library that couples two independently deployable services.
     */
    static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Trust boundary: these numbers arrive from a browser. */
    private static void validate(double lat, double lng, String label) {
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException(label + " latitude must be between -90 and 90, got " + lat);
        }
        if (Double.isNaN(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException(label + " longitude must be between -180 and 180, got " + lng);
        }
    }
}
