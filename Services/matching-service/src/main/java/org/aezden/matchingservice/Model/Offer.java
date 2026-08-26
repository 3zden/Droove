package org.aezden.matchingservice.Model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.UUID;

//@Component
@Getter
@Setter
public class Offer {

    private UUID offerId;
    private UUID tripId;
    private UUID riderId;
    private float pickUpLat;
    private float pickUpLng;
    private float destinationLat;
    private float destinationLng;
    private OfferStatus offerStatus;
    private long fare;

    public Offer(
            UUID tripId,
            UUID riderId,
            float pickUpLat,
            float pickUpLng,
            float destinationLat,
            float destinationLng,
            long fare
    ){
        this.offerId = UUID.randomUUID();
        this.tripId = tripId;
        this.riderId = riderId;
        this.pickUpLat = pickUpLat;
        this.pickUpLng = pickUpLng;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.fare = fare;
        this.offerStatus = OfferStatus.PENDING;

    }
}
