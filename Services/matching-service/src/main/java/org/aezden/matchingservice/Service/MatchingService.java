package org.aezden.matchingservice.Service;


import lombok.extern.slf4j.Slf4j;
import org.aezden.matchingservice.Model.DriverStatus;
import org.aezden.matchingservice.Model.MatchState;
import org.aezden.matchingservice.Model.OfferStatus;
import org.springframework.data.redis.connection.RedisGeoCommands;
import lombok.RequiredArgsConstructor;
import org.aezden.matchingservice.Dto.DriverDto;
import org.aezden.matchingservice.Dto.MatchRequest;
import org.aezden.matchingservice.Model.Offer;
import org.aezden.matchingservice.Producer.NotificationPublisher;
import org.springframework.data.geo.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {
    //  final private DriverRepo driverRepo;
    final private NotificationPublisher notificationPublisher;
    final private StringRedisTemplate stringRedisTemplate;
    final private RedisTemplate<String, Offer> redisOfferTemplate;
    final private RedisTemplate<String, MatchState> redisMatchTemplate;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);


//  matching logic
    public void match(MatchRequest matchRequest) {
        List<DriverDto> selectedDrivers = findNearestDrivers(matchRequest.pickUpLat(), matchRequest.pickUpLng());

//      No Drivers Found
        if (selectedDrivers.isEmpty()){
            log.info("No drivers Found for trip :{}", matchRequest.tripId());
            return;
        }

//      Create a matchState track the current state of the match request
        MatchState matchState = new MatchState(
                matchRequest,
                matchRequest.tripId(),
                selectedDrivers,
                0,
                null);

//      store the current state in redis
        redisMatchTemplate.opsForValue().set(
                "match:" + matchState.getTripId(),
                matchState
        );
        sendOfferToCurrentDriver(matchState.getTripId());
    }


    private void sendOfferToCurrentDriver(UUID tripId) {
//      Getting the current match state
        MatchState matchState = redisMatchTemplate.opsForValue().get(
                "match:" + tripId
        );
//      exceed the selected drivers
        if (matchState.getIndex() >= matchState.getSelectedDrivers().size()){
            log.info("NO DRIVER FOUND");
            return;
        }

//      current driver become unavailable
        UUID currentDriverId = matchState.getSelectedDrivers().get(matchState.getIndex()).driverId();
        if (!isAvailable(currentDriverId)){
            log.info("DRIVER WITH ID: {} IS BUSY...", currentDriverId );
            matchState.setIndex(matchState.getIndex()+1);
            redisMatchTemplate.opsForValue().set(
                    "match:" + tripId,
                    matchState
            );
            sendOfferToCurrentDriver(tripId);
            return;
        }

        Offer offer = createOffer(
                matchState.getMatchRequest(), currentDriverId
        );

//      store the new state of matchState with new offerId
        matchState.setCurrentOfferId(offer.getOfferId());
        redisMatchTemplate.opsForValue().set(
                "match:" + matchState.getTripId(),
                matchState
        );


//      store the offer in redis with 15s TTL
        redisOfferTemplate.opsForValue().set(
                "offer:" + offer.getOfferId(),
                offer,
                Duration.ofSeconds(30)
        );

//      publish the notif
        notificationPublisher.publish(currentDriverId, offer);
        scheduler.schedule(
                () -> handleOfferTimeout(matchState.getTripId(), offer.getOfferId()),
                15,
                TimeUnit.SECONDS
        );
    }


    //   Searching Nearest available drivers
    public List<DriverDto> findNearestDrivers(double lat, double lng){
        Circle circle = new Circle(
                new Point(lng, lat),
                new Distance(3, Metrics.KILOMETERS));

        GeoResults<RedisGeoCommands.GeoLocation<String>> result =
                stringRedisTemplate.opsForGeo()
                        .radius(
                                "drivers:geo",
                                circle,
                                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                        .includeDistance()
                                        .sortAscending());

        return result.getContent()
                .stream()
                .map(res -> {
                    UUID driverId = UUID.fromString(res.getContent().getName());
                    return new DriverDto(
                            driverId,
                            res.getContent().getPoint().getX(),
                            res.getContent().getPoint().getY()
                    );
                })
                .filter(driver -> isAvailable(driver.driverId()))
                .toList();
    }

//  Driver availability
    public boolean isAvailable(UUID driverId){
        String status = stringRedisTemplate.opsForValue().get(
                "driver:" + driverId + ":status"
        );
        return DriverStatus.AVAILABLE.toString().equals(status);
    }


//  Creating Offer
    public Offer createOffer(MatchRequest matchRequest, UUID driverId){
        return new Offer(
                matchRequest.tripId(),
                driverId,
                matchRequest.userId(),
                matchRequest.pickUpLat(),
                matchRequest.pickUpLng(),
                matchRequest.destinationLat(),
                matchRequest.destinationLng(),
                matchRequest.fare()
        );
    }


    private void handleOfferTimeout(UUID tripId, UUID offerId){
        MatchState matchState = redisMatchTemplate.opsForValue().get(
                "match:" + tripId
        );

        if (matchState == null)
            return;

        if (!offerId.equals(matchState.getCurrentOfferId())){
            return;
        }

        Offer offer = redisOfferTemplate.opsForValue().get(
                "offer:" + matchState.getCurrentOfferId()
        );

        if (offer != null && offer.getOfferStatus() == OfferStatus.ACCEPTED){
            return;
        }

        moveToNextDriver(tripId, offerId);
    }


    public ResponseEntity<Offer> acceptOffer(UUID offerId) {

        String key = "offer:" + offerId;
        Offer offer = redisOfferTemplate.opsForValue().get(key);

        if (offer == null){
            log.info("THIS OFFER IS EXPIRED, YOU CANT ACCEPT IT");
            return ResponseEntity.notFound().build();
        }
        MatchState matchState = redisMatchTemplate.opsForValue().get(
                "match:" + offer.getTripId()
        );
        if (matchState == null ||
                !offerId.equals(matchState.getCurrentOfferId())) {

            return ResponseEntity.badRequest().build();
        }
        offer.setOfferStatus(OfferStatus.ACCEPTED);
        redisOfferTemplate.opsForValue().set(key, offer);
        return ResponseEntity.ok(offer);
    }

    public ResponseEntity<Offer> declineOffer(UUID offerId) {
        Offer offer = redisOfferTemplate.opsForValue().get("offer:" + offerId.toString());
        if (offer == null){
            log.info("THIS OFFER IS EXPIRED, TOU CANT DECLINE IT");
            return ResponseEntity.badRequest().body(offer);
        }
        offer.setOfferStatus(OfferStatus.DECLINED);
        redisOfferTemplate.opsForValue().set("offer:" + offerId, offer);

//      Move to the next candidat
        moveToNextDriver(offer.getTripId(), offer.getOfferId());
        return ResponseEntity.ok(offer);
    }

    private void moveToNextDriver(UUID tripId, UUID offerId){
        String key = "match:" + tripId;
        MatchState matchState = redisMatchTemplate.opsForValue().get(
                key);

        if (matchState == null){
            return;
        }

        if (!offerId.equals(matchState.getCurrentOfferId())){
            return;
        }
        matchState.setIndex(matchState.getIndex()+1);
        redisMatchTemplate.opsForValue().set(key, matchState);
        sendOfferToCurrentDriver(tripId);

    }
}
