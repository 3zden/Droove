package org.aezden.pricingservice.Controller;


import org.aezden.pricingservice.Model.PricingResponse;
import org.aezden.pricingservice.Services.PricingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PricingController {
    private PricingService pricingService;
    public PricingController(PricingService pricingService){
        this.pricingService = pricingService;
    }

    @GetMapping("/quote")
    public PricingResponse calculateFare(@RequestParam double pickupLat,
                                         @RequestParam double pickupLng,
                                         @RequestParam double dropLat,
                                         @RequestParam double dropLng){
        return pricingService.getPricing(pickupLat, pickupLng, dropLat, dropLng);
    }
}
