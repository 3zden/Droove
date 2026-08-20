package org.aezden.tripservice.Adapters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aezden.tripservice.Events.TripEvent;
import org.aezden.tripservice.Ports.TripEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaTripEventPublisher implements TripEventPublisher {
    private final KafkaTemplate<String, TripEvent> kafkaTemplate;


    @Override
    public void publish(TripEvent event) {
        log.info("Trip event published Successfully!, and the trip now is: " + event.eventType());
        Message<TripEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "trip-topic")
                .build();
        kafkaTemplate.send(message);
    }
}
