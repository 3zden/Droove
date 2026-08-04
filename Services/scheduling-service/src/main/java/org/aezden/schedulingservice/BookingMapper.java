package org.aezden.schedulingservice;

import org.aezden.schedulingservice.DTOs.BookingResponse;
import org.aezden.schedulingservice.Entities.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking){
        return new BookingResponse(booking.getBookingId(), booking.getRiderId(), booking.getBookingStatus(),booking.getPickUpTime());
    }

}
