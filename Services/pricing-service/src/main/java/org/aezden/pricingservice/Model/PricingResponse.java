package org.aezden.pricingservice.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component @AllArgsConstructor @NoArgsConstructor @Data
public class PricingResponse {
    private long fareCents;
    private double distanceKm;
    private double durationMin;
    private float surge;
}
