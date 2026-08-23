package org.aezden.matchingservice.Dto;

import java.util.UUID;

public record MatchRequest (
        UUID eventId,
        UUID tripId,
        UUID userId,
        float pickUpLat,
        float pickUpLng,
        float destinationLat,
        float destinationLng,
        long fare
) {
}
