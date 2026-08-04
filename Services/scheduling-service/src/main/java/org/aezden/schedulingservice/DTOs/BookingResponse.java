package org.aezden.schedulingservice.DTOs;

import org.aezden.schedulingservice.Entities.BookingStatus;

import java.util.Date;
import java.util.UUID;

public record BookingResponse(UUID bookingId, UUID riderId, BookingStatus bookingStatus, Date pickUpTime) {
}
