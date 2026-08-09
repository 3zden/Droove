package org.aezden.schedulingservice.Services;


import org.aezden.schedulingservice.BookingMapper;
import org.aezden.schedulingservice.DTOs.BookingRequest;
import org.aezden.schedulingservice.DTOs.BookingResponse;
import org.aezden.schedulingservice.Entities.Booking;
import org.aezden.schedulingservice.Entities.BookingStatus;
import org.aezden.schedulingservice.Repositries.BookingRepo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private BookingMapper bookingMapper;
    private BookingRepo bookingRepo;
    public BookingService(BookingRepo bookingRepo, BookingMapper bookingMapper){
        this.bookingRepo = bookingRepo;
        this.bookingMapper = bookingMapper;
    }

    public ResponseEntity<BookingResponse> bookingNewTrip(BookingRequest bookingRequest){
        BookingResponse bookingResponse = bookingMapper.toResponse(bookingRepo.save(new Booking(
                bookingRequest.riderId(),
                bookingRequest.pickUpTime(),
                bookingRequest.pickUpLat(),
                bookingRequest.pickUpLng(),
                bookingRequest.dropLat(),
                bookingRequest.dropLng()
                )));
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingResponse);
    }

    public ResponseEntity<List<BookingResponse>> getBookings(UUID riderId) {
        return ResponseEntity.ok(bookingRepo.getAllByRiderId(riderId));
    }

    public ResponseEntity<BookingResponse> cancelBooking(UUID bookingId) {
        Booking booking = bookingRepo.getBookingByBookingId(bookingId);
        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepo.save(booking);
        BookingResponse bookingResp = bookingMapper.toResponse(booking);
        return ResponseEntity.status(HttpStatus.OK).body(bookingResp);
    }
}
