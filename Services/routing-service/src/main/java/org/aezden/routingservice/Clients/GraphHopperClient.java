package org.aezden.routingservice.Clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Talks to a GraphHopper server. The only class in this service that knows GraphHopper's
 * request shape or JSON field names - swapping in OSRM or Valhalla means rewriting this
 * file and nothing else.
 */
@Component
public class GraphHopperClient {

    private final RestClient restClient;
    private final String profile;

    public GraphHopperClient(
            @Value("${routing.graphhopper.base-url}") String baseUrl,
            @Value("${routing.graphhopper.profile}") String profile,
            @Value("${routing.graphhopper.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${routing.graphhopper.read-timeout-ms}") long readTimeoutMs) {

        this.profile = profile;
        // Timeouts are the whole point of building the client by hand. The JDK's default
        // HTTP client has no read timeout at all, so a hung GraphHopper would pin every
        // request thread here until the pool is exhausted.
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * @return the engine's first path, or null if it returned no usable route.
     * @throws org.springframework.web.client.RestClientException if the engine is
     *         unreachable, times out, or answers with an error status.
     */
    public GhPath route(double fromLat, double fromLng, double toLat, double toLng) {
        GhResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/route")
                        // GraphHopper takes "lat,lng" - the opposite of the GeoJSON it returns.
                        .queryParam("point", fromLat + "," + fromLng)
                        .queryParam("point", toLat + "," + toLng)
                        .queryParam("profile", profile)
                        // We want the geometry: the rider sees the route drawn on the map.
                        .queryParam("points_encoded", false)
                        // We do not want turn-by-turn text - it is the bulk of the payload.
                        .queryParam("instructions", false)
                        .build())
                .retrieve()
                .body(GhResponse.class);

        if (response == null || response.paths() == null || response.paths().isEmpty()) {
            return null;
        }
        return response.paths().getFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GhResponse(List<GhPath> paths) {}

    /** {@code distance} is metres, {@code time} is <em>milliseconds</em>. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GhPath(double distance, long time, GhPoints points) {}

    /** GeoJSON LineString: each coordinate is [lng, lat], not [lat, lng]. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GhPoints(List<List<Double>> coordinates) {}
}
