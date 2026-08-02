package org.aezden.tripservice.Events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TripEvent(UUID eventId, String eventType,UUID tripId, UUID userId, UUID driverId, long fare, Instant completedAt) {}
