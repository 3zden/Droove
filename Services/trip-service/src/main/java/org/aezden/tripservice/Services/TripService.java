package org.aezden.tripservice.Services;

import org.aezden.tripservice.DTOs.CreateTripRequest;
import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Entities.Trip;
import org.aezden.tripservice.Repositries.TripRepo;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

@Service
public class TripService {
    private TripRepo tripRepo;

    public TripService(TripRepo tripRepo){
        this.tripRepo = tripRepo;
    }

    public TripResponse requestTrip(CreateTripRequest createTripRequest){
        Trip TempTrip = tripRepo.save(new Trip(createTripRequest.pickUp(), createTripRequest.destination()));
        return new TripResponse(
                TempTrip.getTripId(),
                Arrays.toString(TempTrip.getDestination()),
                Arrays.toString(TempTrip.getPickUp()),
                TempTrip.getDriverId(),
                TempTrip.getFare(),
                TempTrip.getTripStatus());
    }

    public TripResponse getTrip(UUID tripId) {
        return tripRepo.findTripByTripId(tripId);
    }
}
