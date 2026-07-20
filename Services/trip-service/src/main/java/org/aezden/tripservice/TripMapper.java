package org.aezden.tripservice;

import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Entities.Trip;

import java.util.Arrays;

public class TripMapper {
    public TripResponse requestToResponseMapper(Trip trip){
        return new TripResponse(trip.getTripId(), Arrays.toString(trip.getDestination()),Arrays.toString(trip.getPickUp()),trip.getDriverId(),trip.getFare(),trip.getTripStatus());
    }
}
