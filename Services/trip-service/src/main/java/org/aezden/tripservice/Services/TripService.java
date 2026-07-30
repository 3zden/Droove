package org.aezden.tripservice.Services;

import org.aezden.tripservice.DTOs.CreateTripRequest;
import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Entities.Trip;
import org.aezden.tripservice.Entities.TripStatus;
import org.aezden.tripservice.Repositries.TripRepo;
import org.aezden.tripservice.TripMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

//import java.util.Time;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class TripService {
    private TripRepo tripRepo;
    private TripMapper tripMapper;

    public TripService(TripRepo tripRepo, TripMapper tripMapper){
        this.tripMapper = tripMapper;
        this.tripRepo = tripRepo;
    }

    public TripResponse requestTrip(CreateTripRequest createTripRequest){
        Trip TempTrip = tripRepo.save(new Trip(createTripRequest.pickUp(), createTripRequest.destination()));
        return new TripResponse(
                TempTrip.getTripId(),
                Arrays.toString(TempTrip.getDestination()),
                Arrays.toString(TempTrip.getPickUp()),
                TempTrip.getDriverId(),
                TempTrip.getFare(),
                TempTrip.getTripStatus());
    }

    public ResponseEntity<TripResponse> getTrip(UUID tripId) {
        return ResponseEntity.status(HttpStatus.OK).body(tripMapper
                .requestToResponseMapper(tripRepo.findTripByTripId(tripId)));
    }

    public ResponseEntity<TripResponse> cancelTrip(UUID tripId){
        System.out.println("trip with id :" + tripId +" is Canceled.");
        return ResponseEntity.status(HttpStatus.GONE).body(tripRepo.removeTripByTripId(tripId));
    }

    public ResponseEntity<List<TripResponse>> getAllTrips(UUID userId){
        List<Trip> temp = tripRepo.getAllByUserId(userId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(temp.stream()
                        .map(tripMapper::requestToResponseMapper).toList());
    }


    public ResponseEntity<TripResponse> startTrip(UUID tripId) {
        Date startDate = new Date();
        Trip trip = tripRepo.findTripByTripId(tripId);
        trip.setStartedAt(startDate);
        trip.setTripStatus(TripStatus.ONGOING);
        return ResponseEntity.status(HttpStatus.CREATED).body(tripMapper.requestToResponseMapper(trip));
    }
}
