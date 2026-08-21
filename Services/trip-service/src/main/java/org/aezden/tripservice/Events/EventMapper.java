package org.aezden.tripservice.Events;

import org.aezden.tripservice.Entities.Trip;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventMapper {
//  for Trip events
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
//  for matching events

    public MatchingEvent toMatchingEvent(Trip trip){
        return new MatchingEvent(
                UUID.randomUUID(),
                trip.getTripId(),
                trip.getUserId(),
                trip.getPickUpLat(),
                trip.getPickUpLon(),
                trip.getDestinationLat(),
                trip.getDestinationLon(),
                trip.getFare());
    }
}
