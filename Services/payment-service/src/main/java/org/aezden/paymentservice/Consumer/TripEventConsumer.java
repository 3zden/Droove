package org.aezden.paymentservice.Consumer;

import org.aezden.paymentservice.Events.TripEventMessage;
import org.aezden.paymentservice.Service.LedgerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TripEventConsumer {
    private final LedgerService ledgerService;

    public TripEventConsumer(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @KafkaListener(
            topics = {"${payment.trip-events-topic:trip-events}", "trip-topic"},
            groupId = "${spring.kafka.consumer.group-id:payment-service}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${payment.kafka.enabled:true}")
    public void consume(TripEventMessage event) {
        if (event.eventType() == null) {
            return;
        }
        switch (event.eventType().trim().toUpperCase().replace('-', '_')) {
            case "TRIP_MATCHED" -> ledgerService.handle(LedgerService.TripEventType.MATCHED,
                    event.tripId(), event.userId(), event.driverId(), event.fare());
            case "TRIP_COMPLETED" -> ledgerService.handle(LedgerService.TripEventType.COMPLETED,
                    event.tripId(), event.userId(), event.driverId(), event.fare());
            case "TRIP_CANCELLED", "TRIP_CANCELED" -> ledgerService.handle(LedgerService.TripEventType.CANCELLED,
                    event.tripId(), event.userId(), event.driverId(), event.fare());
            default -> {
                // Payment service intentionally ignores unrelated trip lifecycle events.
            }
        }
    }
}
