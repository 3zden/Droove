package org.aezden.pricingservice.Services;

import org.aezden.pricingservice.Model.PricingResponse;
import org.springframework.stereotype.Service;

import static java.lang.Math.round;

@Service
public class PricingService {
    private final long baseFare = 700;
    private float surge = 1f;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private PricingResponse pricingResponse;

    public PricingService(PricingResponse pricingResponse){
        this.pricingResponse = pricingResponse;
    }

    public double calculateDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2) {

        double latDistance = Math.toRadians(latitude2 - latitude1);
        double lonDistance = Math.toRadians(longitude2 - longitude1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    public PricingResponse getPricing(double pickUpLat,
                                      double pickUpLon,
                                      double destinationLat,
                                      double destinationLon) {
    double distanceKm = calculateDistance(pickUpLat, pickUpLon, destinationLat,destinationLon);
    double durationMin = (distanceKm/30)*60;
    long fare = Math.max(baseFare, round((500 + 120*distanceKm + 30*durationMin) * surge));
    return new PricingResponse(fare, distanceKm, durationMin, surge);
    }
}
