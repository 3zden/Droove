package org.aezden.tripservice.Ports;


import lombok.RequiredArgsConstructor;
import org.aezden.tripservice.Events.TripEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TripEventPublisher {
    private final KafkaTemplate<String, TripEvent> kafkaTemplate;

    public void sendTripMessage(TripEvent tripEvent) {
        Message<TripEvent> message = MessageBuilder
                .withPayload(tripEvent)
                .setHeader(KafkaHeaders.TOPIC, "trip-topic")
                .build();

        kafkaTemplate.send(message);
    }
}
