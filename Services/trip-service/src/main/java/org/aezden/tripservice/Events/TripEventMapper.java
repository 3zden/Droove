package org.aezden.tripservice.Events;

import org.aezden.tripservice.Entities.Trip;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TripEventMapper {
    public TripEvent toEvent(Trip trip, TripEventType type){
        return new TripEvent(
                UUID.randomUUID(),
                type.toString(),
                trip.getTripId(),
                trip.getUserId(),
                trip.getDriverId(),
                trip.getFare(),
                trip.getCompletedAt());
    }
}
