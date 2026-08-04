package org.aezden.schedulingservice.DTOs;

import java.util.Date;
import java.util.UUID;

public record BookingRequest(UUID riderId, Date pickUpTime, double pickUpLat, double pickUpLng, double dropLat, double dropLng) {
}
