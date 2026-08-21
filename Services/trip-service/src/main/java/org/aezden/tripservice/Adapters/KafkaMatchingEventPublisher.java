package org.aezden.tripservice.Adapters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aezden.tripservice.Events.MatchingEvent;
import org.aezden.tripservice.Ports.TripEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaMatchingEventPublisher implements TripEventPublisher<MatchingEvent> {
    private final KafkaTemplate<String, MatchingEvent> kafkaTemplate;


    @Override
    public void publish(MatchingEvent event) {
        log.info("Matching event published Successfully!, and the trip now is: Matching");
        Message<MatchingEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "matching-topic")
                .build();
        kafkaTemplate.send(message);
    }
}
