package org.aezden.tripservice.Controller;


import org.aezden.tripservice.DTOs.CreateTripRequest;
import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Services.TripService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class TripController {
    private TripService tripService;

    public TripController(TripService tripService){
        this.tripService = tripService;
    }

    @PostMapping("/api/trips/")
    public TripResponse requestTrip(@RequestBody CreateTripRequest createTripRequest){
        return tripService.requestTrip(createTripRequest);
    }

    @GetMapping("/api/trips/{id}")
    public TripResponse getTrip(@RequestParam UUID tripId){
        return tripService.getTrip(tripId);
    }
}
