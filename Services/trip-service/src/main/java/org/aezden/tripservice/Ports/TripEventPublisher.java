package org.aezden.tripservice.Ports;

import org.aezden.tripservice.Events.MatchRequest;
import org.aezden.tripservice.Events.TripEvent;

public interface TripEventPublisher<Event> {
    void publish(Event event);
}
