package org.aezden.tripservice.DTOs;

import org.aezden.tripservice.Entities.TripStatus;

import java.util.UUID;

public record TripResponse(
        UUID tripId, String destination, String pickUp, UUID driverId, double fare, TripStatus tripStatus
) {
}
