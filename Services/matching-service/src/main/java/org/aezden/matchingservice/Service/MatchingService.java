package org.aezden.matchingservice.Service;


import org.aezden.matchingservice.Model.OfferStatus;
import org.springframework.data.redis.connection.RedisGeoCommands;
import lombok.RequiredArgsConstructor;
import org.aezden.matchingservice.Dto.DriverDto;
import org.aezden.matchingservice.Dto.MatchRequest;
import org.aezden.matchingservice.Model.Offer;
import org.aezden.matchingservice.Producer.NotificationPublisher;
import org.aezden.matchingservice.Repo.DriverRepo;
import org.springframework.data.geo.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private DriverRepo driverRepo;
    private NotificationPublisher notificationPublisher;
    private StringRedisTemplate stringRedisTemplate;
    private RedisTemplate<String, Offer> redisTemplate;

//  selecting and sending each driver the ride offer
    public void match(MatchRequest matchRequest) {
        List<DriverDto> selectedDrivers = findNearestDrivers(matchRequest.pickUpLat(), matchRequest.pickUpLng());
        Offer offer = createOffer(matchRequest);
        for(DriverDto driver: selectedDrivers){
            System.out.printf("driver with id" + driver.driverId() + "in position" + driver.lat() +driver.lng());
//          sending offer for each driver
            notificationPublisher.publish(driver.driverId(), offer);

        }
    }

//  Searching Nearest available drivers
    public List<DriverDto> findNearestDrivers(double lat, double lng){
        Circle circle = new Circle(
                new Point(lat, lng),
                new Distance(3, Metrics.KILOMETERS)
        );
        GeoResults<RedisGeoCommands.GeoLocation<String>> result =
                stringRedisTemplate.opsForGeo()
                        .radius(
                                "drivers:locations",
                                circle,
                                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                        .includeDistance()
                                        .sortAscending()
                        );
        return result.getContent()
                .stream().map(res -> new DriverDto(
                        UUID.fromString(res.getContent().getName()),
                        res.getContent().getPoint().getX(),
                        res.getContent().getPoint().getY())
                )
                .toList();
    }

//  Creating Offer
    public Offer createOffer(MatchRequest matchRequest){
        return new Offer(
                matchRequest.tripId(),
                matchRequest.userId(),
                matchRequest.pickUpLat(),
                matchRequest.pickUpLng(),
                matchRequest.destinationLat(),
                matchRequest.destinationLng(),
                matchRequest.fare()
        );
    }


    public ResponseEntity<Offer> acceptOffer(UUID offerId) {
        Offer offer = redisTemplate.opsForValue().getAndPersist(offerId.toString());
        offer.setOfferStatus(OfferStatus.ACCEPTED);
        redisTemplate.opsForValue().set(offerId.toString(), offer);
        return ResponseEntity.ok(offer);
    }

    public ResponseEntity<Offer> declineOffer(UUID offerId) {
        Offer offer = redisTemplate.opsForValue().getAndPersist(offerId.toString());
        offer.setOfferStatus(OfferStatus.DECLINED);
        redisTemplate.opsForValue().set(offerId.toString(), offer);
        return ResponseEntity.ok(offer);
    }

}
