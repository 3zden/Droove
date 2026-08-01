package org.aezden.tripservice.DTOs;

import org.aezden.tripservice.Entities.TripStatus;

import java.util.UUID;

public record TripResponse(
        UUID tripId, float destinationLat, float destinationLon, float pickUpLat,float pickUpLon, UUID driverId, double fare, TripStatus tripStatus
) {
}
