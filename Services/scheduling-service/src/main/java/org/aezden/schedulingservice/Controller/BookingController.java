package org.aezden.schedulingservice.Controller;


import org.aezden.schedulingservice.DTOs.BookingRequest;
import org.aezden.schedulingservice.DTOs.BookingResponse;
import org.aezden.schedulingservice.Services.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class BookingController {
    private BookingService bookingService;
    public BookingController(BookingService bookingService){
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> bookingNewTrip(@RequestBody BookingRequest bookingRequest){
        return bookingService.bookingNewTrip(bookingRequest);
    }

    @GetMapping("/bookings/mine")
    public ResponseEntity<List<BookingResponse>> getBookings(){
        return bookingService.getBookings();
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(@RequestParam UUID bookingId){
        return bookingService.cancelBooking(bookingId);
    }
}
