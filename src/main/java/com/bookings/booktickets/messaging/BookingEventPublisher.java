package com.bookings.booktickets.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.bookings.booktickets.config.RabbitMqConfig.BOOKING_CREATED_ROUTING_KEY;
import static com.bookings.booktickets.config.RabbitMqConfig.BOOKING_EXCHANGE;

/**
 * Registers a Spring application event during the booking transaction, then relays it to
 * RabbitMQ only after that transaction has actually committed. This avoids publishing a
 * confirmation message for a reservation that ends up rolled back.
 */
@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final RabbitTemplate rabbitTemplate;

    public BookingEventPublisher(ApplicationEventPublisher applicationEventPublisher, RabbitTemplate rabbitTemplate) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishAfterCommit(BookingCreatedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void relayToRabbit(BookingCreatedEvent event) {
        log.info("Publishing booking.created for bookingId={}", event.bookingId());
        rabbitTemplate.convertAndSend(BOOKING_EXCHANGE, BOOKING_CREATED_ROUTING_KEY, event);
    }
}
