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

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {
    //  final private DriverRepo driverRepo;
    final private NotificationPublisher notificationPublisher;
    final private StringRedisTemplate stringRedisTemplate;
    final private RedisTemplate<String, Offer> redisOfferTemplate;
    final private RedisTemplate<String, MatchState> redisMatchTemplate;

//  selecting and sending each driver the ride offer
    public void match(MatchRequest matchRequest) {

        List<DriverDto> selectedDrivers = findNearestDrivers(matchRequest.pickUpLat(), matchRequest.pickUpLng());

//      No Drivers Found
        if (selectedDrivers.isEmpty()){
            log.info("No drivers Found for trip :{}", matchRequest.tripId());
            return;
        }

//      Create a matchState track Current state of the match request
        MatchState matchState = new MatchState(
                matchRequest.tripId(),
                selectedDrivers,
                0,
                null);

//      store the current state in redis
        redisMatchTemplate.opsForValue().set(
                "match:" + matchState.getTripId(),
                matchState
        );
        sendOfferToCurrentDriver(matchState, matchRequest);
    }

    private void sendOfferToCurrentDriver(MatchState matchState, MatchRequest matchRequest) {

//      exceed the selected drivers
        if (matchState.getCurrentDriver() >= matchState.getSelectedDrivers().size()){
            log.info("NO DRIVER FOUND");
            return;
        }

//      current driver become unavailable
        UUID currentDriverId = matchState.getSelectedDrivers().get(matchState.getCurrentDriver()).driverId();
        if (!isAvailable(currentDriverId)){
            log.info("DRIVER WITH ID: {} IS BUSY...",currentDriverId );
            matchState.setCurrentDriver(matchState.getCurrentDriver() + 1);
            sendOfferToCurrentDriver(matchState, matchRequest);
            return;
        }

        Offer offer = createOffer(
                matchRequest, currentDriverId
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
                Duration.ofSeconds(15)
        );

//      publish the notif
        notificationPublisher.publish(currentDriverId, offer);
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
        return status.equals(DriverStatus.AVAILABLE.toString());
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


//    public ResponseEntity<Offer> acceptOffer(UUID offerId) {
//        Offer offer = redisTemplate.opsForValue().getAndPersist(offerId.toString());
//        offer.setOfferStatus(OfferStatus.ACCEPTED);
//        redisTemplate.opsForValue().set(offerId.toString(), offer);
//        return ResponseEntity.ok(offer);
//    }
//
//    public ResponseEntity<Offer> declineOffer(UUID offerId) {
//        Offer offer = redisTemplate.opsForValue().getAndPersist(offerId.toString());
//        offer.setOfferStatus(OfferStatus.DECLINED);
//        redisTemplate.opsForValue().set(offerId.toString(), offer);
//        return ResponseEntity.ok(offer);
//    }



}
