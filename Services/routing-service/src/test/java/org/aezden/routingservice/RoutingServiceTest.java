package org.aezden.routingservice;

import org.aezden.routingservice.Clients.GraphHopperClient;
import org.aezden.routingservice.Model.RouteResponse;
import org.aezden.routingservice.Services.RoutingService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three things worth a test here: the engine's answer is mapped correctly (including
 * the coordinate swap that is easy to get backwards and hard to spot), the fallback
 * triggers instead of throwing, and bad coordinates are rejected.
 */
class RoutingServiceTest {

    // Casablanca -> Mohammedia. Straight line is 22.93 km, so the fallback estimate
    // (x1.3) should land near 29.8 km.
    private static final double FROM_LAT = 33.5731, FROM_LNG = -7.5898;
    private static final double TO_LAT = 33.6866, TO_LNG = -7.3830;

    /** A client whose behaviour the test controls, without a real GraphHopper anywhere. */
    private static RoutingService serviceReturning(GraphHopperClient.GhPath path, RuntimeException failure) {
        GraphHopperClient stub = new GraphHopperClient("http://localhost:1", "car", 100, 100) {
            @Override
            public GraphHopperClient.GhPath route(double a, double b, double c, double d) {
                if (failure != null) throw failure;
                return path;
            }
        };
        return new RoutingService(stub);
    }

    @Test
    void mapsEngineAnswerAndSwapsGeoJsonCoordinatesToLatLng() {
        // GraphHopper speaks GeoJSON: [lng, lat]. Everything downstream expects [lat, lng].
        var enginePath = new GraphHopperClient.GhPath(
                31200.0,
                1_845_000L, // milliseconds
                new GraphHopperClient.GhPoints(List.of(
                        List.of(FROM_LNG, FROM_LAT),
                        List.of(TO_LNG, TO_LAT))));

        RouteResponse route = serviceReturning(enginePath, null).getRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertEquals(RouteResponse.RouteSource.ROAD, route.source());
        assertEquals(31200.0, route.distanceMeters());
        assertEquals(1845, route.durationSeconds(), "milliseconds must be converted to seconds");

        assertEquals(2, route.points().size());
        assertEquals(FROM_LAT, route.points().get(0)[0], 1e-9, "first element must be latitude");
        assertEquals(FROM_LNG, route.points().get(0)[1], 1e-9, "second element must be longitude");
    }

    @Test
    void fallsBackToStraightLineEstimateWhenEngineIsUnreachable() {
        RouteResponse route = serviceReturning(null, new RestClientException("connection refused"))
                .getRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);

        assertEquals(RouteResponse.RouteSource.FALLBACK, route.source());
        // 22.93 km straight line x 1.3 detour factor
        assertEquals(29811, route.distanceMeters(), 200);
        // that distance at 30 km/h
        assertEquals(3577, route.durationSeconds(), 30);
        assertEquals(2, route.points().size(), "a fallback draws the honest straight line");
    }

    @Test
    void fallsBackWhenEngineFindsNoRoute() {
        RouteResponse route = serviceReturning(null, null).getRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);
        assertEquals(RouteResponse.RouteSource.FALLBACK, route.source());
    }

    @Test
    void rejectsCoordinatesOutsideTheEarth() {
        RoutingService service = serviceReturning(null, null);

        assertThrows(IllegalArgumentException.class,
                () -> service.getRoute(500, FROM_LNG, TO_LAT, TO_LNG), "latitude 500 is not a place");
        assertThrows(IllegalArgumentException.class,
                () -> service.getRoute(FROM_LAT, -200, TO_LAT, TO_LNG), "longitude -200 is not a place");
        assertThrows(IllegalArgumentException.class,
                () -> service.getRoute(FROM_LAT, FROM_LNG, Double.NaN, TO_LNG), "NaN must not slip through");
    }

    @Test
    void fallbackDistanceIsAlwaysLongerThanTheStraightLine() {
        RouteResponse route = serviceReturning(null, new RestClientException("down"))
                .getRoute(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG);
        assertTrue(route.distanceMeters() > 22_931,
                "a road route is never shorter than the crow flies");
    }
}
