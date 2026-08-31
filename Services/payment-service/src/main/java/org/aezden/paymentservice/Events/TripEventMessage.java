package org.aezden.paymentservice.Events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TripEventMessage(
        String eventType,
        UUID tripId,
        UUID userId,
        UUID driverId,
        long fare
) {
}
