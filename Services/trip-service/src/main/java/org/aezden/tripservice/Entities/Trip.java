package org.aezden.tripservice.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Data @Getter @Setter
@Entity(name="rides")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID tripId;
    private UUID userId;
    private UUID driverId;
    private long fare;
    private float pickUpLat;
    private float pickUpLon;
    private float destinationLat;
    private float destinationLon;
    private Date requestedAt;
    private Date startedAt;
    private Date completedAt;
    private TripStatus tripStatus;

    public Trip(UUID userId, float pickUpLat, float pickUpLon, float destinationLat, float destinationLon){
        this.userId = userId;
        this.tripStatus = TripStatus.REQUESTED;
        this.pickUpLat = pickUpLat;
        this.destinationLat = destinationLat;
        this.pickUpLon = pickUpLon;
        this.destinationLon = destinationLon;
        this.requestedAt = new Date();
        this.fare = 0L;
    }

    public Trip() {
    }
}
