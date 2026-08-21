package org.aezden.tripservice.Services;

import org.aezden.tripservice.DTOs.CreateTripRequest;
import org.aezden.tripservice.DTOs.TripResponse;
import org.aezden.tripservice.Domain.TripStateMachine;
import org.aezden.tripservice.Entities.Trip;
import org.aezden.tripservice.Entities.TripStatus;
import org.aezden.tripservice.Events.MatchingEvent;
import org.aezden.tripservice.Events.TripEvent;
import org.aezden.tripservice.Events.EventMapper;
import org.aezden.tripservice.Events.TripEventType;
import org.aezden.tripservice.Exceptions.TripNotFoundException;
import org.aezden.tripservice.Ports.TripEventPublisher;
import org.aezden.tripservice.Repositries.TripRepo;
import org.aezden.tripservice.TripMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class TripService {
    private TripRepo tripRepo;
    private TripMapper tripMapper;
    private TripStateMachine tripStateMachine;

//  Kafka fields
    private TripEventPublisher<TripEvent> tripEventPublisher;
    private EventMapper eventMapper;
    private TripEventPublisher<MatchingEvent> matchingEventPublisher;

    public TripService(TripRepo tripRepo,
                       TripMapper tripMapper,
                       TripStateMachine tripStateMachine,
                       TripEventPublisher<TripEvent> tripEventPublisher,
                       EventMapper eventMapper,
                       TripEventPublisher<MatchingEvent> matchingEventPublisher
    ){
        this.tripMapper = tripMapper;
        this.eventMapper = eventMapper;
        this.tripRepo = tripRepo;
        this.tripStateMachine = tripStateMachine;
        this.tripEventPublisher = tripEventPublisher;
        this.matchingEventPublisher = matchingEventPublisher;
    }

    public Trip getTripOrThrow(UUID tripId){
        return tripRepo.findTripByTripId(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
    }

    public TripResponse requestTrip(CreateTripRequest createTripRequest){
        Trip tempTrip = tripRepo.save(new Trip(createTripRequest.userId(), createTripRequest.pickUpLat(), createTripRequest.pickUpLon(), createTripRequest.destinationLat(),createTripRequest.destinationLon()));
        tripEventPublisher.publish(eventMapper.toEvent(tempTrip, TripEventType.TRIP_REQUESTED));
        matchingEventPublisher.publish(eventMapper.toMatchingEvent(tempTrip));
        return new TripResponse(
                tempTrip.getTripId(),
                tempTrip.getDestinationLat(),
                tempTrip.getDestinationLon(),
                tempTrip.getPickUpLat(),
                tempTrip.getPickUpLon(),
                tempTrip.getDriverId(),
                tempTrip.getFare(),
                tempTrip.getTripStatus());
    }

    public ResponseEntity<TripResponse> getTrip(UUID tripId) {
        return ResponseEntity.status(HttpStatus.OK).body(tripMapper
                .requestToResponseMapper(getTripOrThrow(tripId)));
    }


    public ResponseEntity<List<TripResponse>> getAllTrips(UUID userId){
        List<Trip> temp = tripRepo.getAllByUserId(userId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(temp.stream()
                        .map(tripMapper::requestToResponseMapper).toList());
    }


    public ResponseEntity<TripResponse> startTrip(UUID tripId) {
        Trip tempTrip = getTripOrThrow(tripId);
        if (!tripStateMachine.isValid(TripStatus.ONGOING, tempTrip.getTripStatus())){
            System.out.println("You cant Start this "+ tempTrip.getTripStatus() + " Trip");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(tripMapper.requestToResponseMapper(tempTrip));
        }
        tempTrip.setStartedAt(new Date());
        tempTrip.setTripStatus(TripStatus.ONGOING);
        tripRepo.save(tempTrip);

//      publish events to kafka - topic = trip-topic
        tripEventPublisher.publish(eventMapper.toEvent(tempTrip, TripEventType.TRIP_STARTED));
        return ResponseEntity.status(HttpStatus.CREATED).body(tripMapper.requestToResponseMapper(tempTrip));
    }

    public ResponseEntity<TripResponse> completeTrip(UUID tripId) {
        Trip tempTrip = getTripOrThrow(tripId);
        if (!tripStateMachine.isValid(TripStatus.COMPLETED, tempTrip.getTripStatus())){
            System.out.println("You cant Complete this "+ tempTrip.getTripStatus() + " Trip");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(tripMapper.requestToResponseMapper(tempTrip));
        }
        tempTrip.setTripStatus(TripStatus.COMPLETED);
        tripRepo.save(tempTrip);

//      publish event kafka trip-topi
        tripEventPublisher.publish(eventMapper.toEvent(tempTrip, TripEventType.TRIP_COMPLETED));
        return ResponseEntity.status(HttpStatus.CREATED).body(tripMapper.requestToResponseMapper(tempTrip));
    }

    public ResponseEntity<TripResponse> arrivedDriver(UUID tripId) {
        Trip tempTrip = getTripOrThrow(tripId);
//      Checking if this transition is valid
        if (!tripStateMachine.isValid(TripStatus.DRIVER_ARRIVED, tempTrip.getTripStatus())){
            System.out.println("You cant change this "+ tempTrip.getTripStatus() + " Trip to driver arrived");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(tripMapper.requestToResponseMapper(tempTrip));
        }
        tempTrip.setTripStatus(TripStatus.DRIVER_ARRIVED);
        tripRepo.save(tempTrip);
        tripEventPublisher.publish(eventMapper.toEvent(tempTrip, TripEventType.DRIVER_ARRIVED));
        return ResponseEntity.status(HttpStatus.CREATED).body(tripMapper.requestToResponseMapper(tempTrip));
    }

    public ResponseEntity<TripResponse> cancelTrip(UUID tripId){
        Trip tempTrip = getTripOrThrow(tripId);
//      Checking if this transition is valid
        if (!tripStateMachine.isValid(TripStatus.CANCELLED, tempTrip.getTripStatus())){
            System.out.println("You cant cancel this "+ tempTrip.getTripStatus() + " Trip");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(tripMapper.requestToResponseMapper(tempTrip));
        }
        tempTrip.setTripStatus(TripStatus.CANCELLED);
        tripRepo.save(tempTrip);
        tripEventPublisher.publish(eventMapper.toEvent(tempTrip, TripEventType.TRIP_CANCELED));
        return ResponseEntity.status(HttpStatus.OK).body(tripMapper.requestToResponseMapper(tempTrip));
    }
}
