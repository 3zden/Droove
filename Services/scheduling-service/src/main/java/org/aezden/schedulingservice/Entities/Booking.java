package org.aezden.schedulingservice.Entities;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Entity @Data
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID bookingId;
    private UUID riderId;
    private BookingStatus bookingStatus;
    private Date pickUpTime;
    private double pickUpLat;
    private double pickUpLng;
    private double dropLat;
    private double dropLng;

    protected Booking() {}

    public Booking(UUID riderId, Date pickUpTime, double pickUpLat, double pickUpLng, double dropLat, double dropLng){
        this.riderId = riderId;
        this.pickUpTime = pickUpTime;
        this.pickUpLat = pickUpLat;
        this.pickUpLng = pickUpLng;
        this.dropLat = dropLat;
        this.dropLng = dropLng;
        this.bookingStatus = BookingStatus.SCHEDULED;
    }

}
