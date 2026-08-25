package org.aezden.matchingservice.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aezden.matchingservice.Dto.MatchRequest;
import org.aezden.matchingservice.Service.MatchingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MatchRequestConsumer {
    private MatchingService matchingService;

    @KafkaListener(topics = "match-requests", groupId = "matching-group")
    public void consumeEvents(MatchRequest matchRequest){
        log.info("were now starting consuming match-requests events" + matchRequest.toString());
        matchingService.match(matchRequest);
    }
}
