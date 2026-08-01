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

@RestController
public class TripController {
    private TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    //  Making a new trip request
    @PostMapping("/api/trips/")
    public TripResponse requestTrip(@RequestBody CreateTripRequest createTripRequest) {
        return tripService.requestTrip(createTripRequest);
    }

    //  Getting a trip with its id
    @GetMapping("/api/trips/{tripId}")
    public ResponseEntity<TripResponse> getTrip(@PathVariable UUID tripId) {
        return tripService.getTrip(tripId);
    }

    //  Showing all history of trips
    @GetMapping("/api/trips/mine")
    public ResponseEntity<List<TripResponse>> getAllTrips(@RequestHeader("X-User-Id") UUID userId) {
        return tripService.getAllTrips(userId);
    }

    //  Cancel a trips using its id
    @PostMapping("/api/trips/{tripId}/cancel")
    public ResponseEntity<TripResponse> cancelTrip(@PathVariable UUID tripId) {
        return tripService.cancelTrip(tripId);
    }

    //  Assign a specific driver to a trip
    @PatchMapping("api/trips/{tripId}/assign")
    public ResponseEntity<TripResponse> assignDriver(@PathVariable UUID tripId) {
        return tripService.getTrip(tripId);
    }

    //  rider arrived (rider is sus ngl.)
    @PostMapping("api/trips/{tripId}/arrived")
    public ResponseEntity<TripResponse> arrivedDriver(@PathVariable UUID tripId){
        return tripService.arrivedDriver(tripId);
    }

    //  Start the Trip
    @PostMapping("api/trips/{tripId}/start")
    public ResponseEntity<TripResponse> startTrip(@PathVariable UUID tripId) {
        return tripService.startTrip(tripId);
    }

    //  the ride is complete
    @PostMapping("api/trips/{tripId}/complete")
    public ResponseEntity<TripResponse> completeTrip(@PathVariable UUID tripId){
        return tripService.completeTrip(tripId);
    }

    //  Trip not found exception handling method
    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<String> handleNotFound(TripNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }



}