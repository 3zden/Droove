package org.aezden.tripservice.Events;

import java.time.Instant;
import java.util.UUID;

public record TripRequestedEvent(UUID tripId, UUID userId, float pickUpLat, float pickUpLon, Instant requestedAt) {}
