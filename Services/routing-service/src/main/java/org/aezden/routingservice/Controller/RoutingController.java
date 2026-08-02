package org.aezden.routingservice.Controller;

import org.aezden.routingservice.Model.RouteResponse;
import org.aezden.routingservice.Services.RoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unprefixed on purpose: the gateway owns the /api/routing prefix and strips it before
 * forwarding. The service must not hardcode its own public path.
 */
@RestController
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/route")
    public RouteResponse route(@RequestParam double fromLat,
                               @RequestParam double fromLng,
                               @RequestParam double toLat,
                               @RequestParam double toLng) {
        return routingService.getRoute(fromLat, fromLng, toLat, toLng);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadCoordinates(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
