package org.aezden.tripservice.Repositries;

import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Entities.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TripRepo extends JpaRepository<Trip, UUID>{
    TripResponse findTripByTripId(UUID tripId);
}
