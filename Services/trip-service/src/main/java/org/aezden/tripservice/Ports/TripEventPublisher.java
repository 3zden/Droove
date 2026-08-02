package org.aezden.tripservice.Ports;


import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TripEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TripEventPublisher(KafkaTemplate<String, Object> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UUID tripId, Object event){
        kafkaTemplate.send("trip-events", tripId.toString(), event);
    }
}
