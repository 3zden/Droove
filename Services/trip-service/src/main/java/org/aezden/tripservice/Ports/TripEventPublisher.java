package org.aezden.tripservice.Ports;

import org.aezden.tripservice.Events.TripEvent;

public interface TripEventPublisher {
    void publish(TripEvent event);
}
