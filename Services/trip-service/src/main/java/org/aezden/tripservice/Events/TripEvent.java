package org.aezden.tripservice.Events;

import java.util.Date;
import java.util.UUID;

public record TripEvent(UUID eventId, String eventType,UUID tripId, UUID userId, UUID driverId, long fare, Date completedAt) {}
