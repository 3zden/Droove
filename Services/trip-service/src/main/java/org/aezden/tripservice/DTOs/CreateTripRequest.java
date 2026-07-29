package org.aezden.tripservice.DTOs;

public record CreateTripRequest(
        double[] pickUp, double[] destination
) {
}
