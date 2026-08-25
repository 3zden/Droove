package org.aezden.matchingservice.Dto;

import java.util.UUID;

public record DriverDto(UUID driverId, double lat, double lng) {
}
