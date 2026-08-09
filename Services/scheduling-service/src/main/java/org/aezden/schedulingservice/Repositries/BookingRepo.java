package org.aezden.schedulingservice.Repositries;

import org.aezden.schedulingservice.DTOs.BookingResponse;
import org.aezden.schedulingservice.Entities.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepo extends JpaRepository<Booking, UUID> {

    Booking getBookingByBookingId(UUID bookingId);

    List<BookingResponse> getAllByRiderId(UUID riderId);
}
