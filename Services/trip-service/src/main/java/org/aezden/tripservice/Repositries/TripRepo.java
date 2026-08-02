package org.aezden.tripservice.Repositries;

import org.aezden.tripservice.Entities.Trip;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepo extends JpaRepository<Trip, UUID>{
    Optional<Trip> findTripByTripId(UUID tripId);
    List<Trip> getAllByUserId(UUID userId);
}
