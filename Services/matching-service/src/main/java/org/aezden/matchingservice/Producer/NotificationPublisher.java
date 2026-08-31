package org.aezden.matchingservice.Producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aezden.matchingservice.Model.Offer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {
    final KafkaTemplate<String, Offer> kafkaTemplate;

    public void publish(UUID driverId, Offer offer) {
        kafkaTemplate.send(
                "notifications",
                driverId.toString(),
                offer
        );

    }
}
