package org.aezden.tripservice.Controller;


import org.aezden.tripservice.DTOs.CreateTripRequest;
import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Exceptions.TripNotFoundException;
import org.aezden.tripservice.Services.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;

// Unprefixed on purpose: the gateway owns the /api/trips prefix and strips it before
// forwarding. The service must not hardcode its own public path.
@RestController
@RequestMapping("/trips")
public class TripController {
    private TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    //  Making a new trip request
    @PostMapping
    public TripResponse requestTrip(@RequestBody CreateTripRequest createTripRequest) {
        return tripService.requestTrip(createTripRequest);
    }

    //  Getting a trip with its id
    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> getTrip(@PathVariable UUID tripId) {
        return tripService.getTrip(tripId);
    }

    //  Showing all history of trips
    @GetMapping("/mine")
    public ResponseEntity<List<TripResponse>> getAllTrips(@RequestHeader("X-User-Id") UUID userId) {
        return tripService.getAllTrips(userId);
    }

    //  Cancel a trips using its id
    @PostMapping("/{tripId}/cancel")
    public ResponseEntity<TripResponse> cancelTrip(@PathVariable UUID tripId) {
        return tripService.cancelTrip(tripId);
    }

    //  Assign a specific driver to a trip
    @PatchMapping("/{tripId}/assign")
    public ResponseEntity<TripResponse> assignDriver(@PathVariable UUID tripId) {
        return tripService.getTrip(tripId);
    }

    //  rider arrived (rider is sus ngl.)
    @PostMapping("/{tripId}/arrived")
    public ResponseEntity<TripResponse> arrivedDriver(@PathVariable UUID tripId){
        return tripService.arrivedDriver(tripId);
    }

    //  Start the Trip
    @PostMapping("/{tripId}/start")
    public ResponseEntity<TripResponse> startTrip(@PathVariable UUID tripId) {
        return tripService.startTrip(tripId);
    }

    //  the ride is complete
    @PostMapping("/{tripId}/complete")
    public ResponseEntity<TripResponse> completeTrip(@PathVariable UUID tripId){
        return tripService.completeTrip(tripId);
    }

    //  Trip not found exception handling method
    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<String> handleNotFound(TripNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }



}