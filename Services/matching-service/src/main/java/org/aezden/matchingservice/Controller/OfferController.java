package org.aezden.matchingservice.Controller;

import lombok.RequiredArgsConstructor;
import org.aezden.matchingservice.Model.Offer;
import org.aezden.matchingservice.Service.MatchingService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/matching/offers")
public class OfferController {
    final private MatchingService matchingService;

    @PostMapping("/{offerId}/accept")
    public ResponseEntity<Offer> acceptOffer(@PathVariable UUID offerId){
        return matchingService.acceptOffer(offerId);
    }

    @PostMapping("/{offerId}/decline")
    public ResponseEntity<Offer> declineOffer(@PathVariable UUID offerId){
        return matchingService.declineOffer(offerId);
    }
}
