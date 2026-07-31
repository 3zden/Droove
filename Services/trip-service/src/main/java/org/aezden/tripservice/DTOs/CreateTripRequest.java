package org.aezden.tripservice.DTOs;

import java.util.UUID;

public record CreateTripRequest(
        UUID userId, float pickUpLat, float pickUpLon, float destinationLat, float destinationLon
) {
}
