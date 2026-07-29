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
    private UUID driverId;
    private Double fare;
    private double[] pickUp;
    private double[] destination;
    private Date requestedAt;
    private Date startedAt;
    private Date completedAt;
    private TripStatus tripStatus;

    public Trip(double[] pickUp, double[] destination){
        this.tripStatus = TripStatus.REQUESTED;
        this.pickUp = pickUp;
        this.destination = destination;
        this.requestedAt = new Date();
        this.fare = 0.0;
    }

    public Trip() {
    }
}
