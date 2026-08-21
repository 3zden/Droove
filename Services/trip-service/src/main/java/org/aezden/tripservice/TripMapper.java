package org.aezden.tripservice;

import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Entities.Trip;
import org.springframework.stereotype.Component;

@Component
public class TripMapper {
    public TripResponse requestToResponseMapper(Trip trip){
        return new TripResponse(trip.getTripId(),
                trip.getDestinationLat(),
                trip.getDestinationLon(),
                trip.getPickUpLat(),
                trip.getPickUpLon(),
                trip.getDriverId(),
                trip.getFare(),
                trip.getTripStatus());
    }


}
