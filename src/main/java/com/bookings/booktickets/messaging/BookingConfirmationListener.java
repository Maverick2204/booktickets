package com.bookings.booktickets.messaging;

import com.bookings.booktickets.service.BookingConfirmationService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.bookings.booktickets.config.RabbitMqConfig.BOOKING_CREATED_ROUTING_KEY;
import static com.bookings.booktickets.config.RabbitMqConfig.BOOKING_EXCHANGE;
import static com.bookings.booktickets.config.RabbitMqConfig.CONFIRMATION_QUEUE;
import static com.bookings.booktickets.config.RabbitMqConfig.RETRY_COUNT_HEADER;

/**
 * Consumes booking.created events and simulates async confirmation work. A "business"
 * outcome (payment approved or declined) is always acked - the booking ends up CONFIRMED
 * or FAILED (with inventory compensated). An unexpected infra exception is retried a
 * bounded number of times by manually republishing with an incremented retry-count header,
 * then finally nacked without requeue so the queue's dead-letter config routes it to the DLQ
 * for operator inspection.
 */
@Component
public class BookingConfirmationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationListener.class);

    private final BookingConfirmationService confirmationService;
    private final PaymentGatewaySimulator paymentGatewaySimulator;
    private final RabbitTemplate rabbitTemplate;
    private final int maxRetries;

    public BookingConfirmationListener(
            BookingConfirmationService confirmationService,
            PaymentGatewaySimulator paymentGatewaySimulator,
            RabbitTemplate rabbitTemplate,
            @Value("${booking.confirmation.max-retries}") int maxRetries) {
        this.confirmationService = confirmationService;
        this.paymentGatewaySimulator = paymentGatewaySimulator;
        this.rabbitTemplate = rabbitTemplate;
        this.maxRetries = maxRetries;
    }

    @RabbitListener(queues = CONFIRMATION_QUEUE)
    public void onBookingCreated(
            BookingCreatedEvent event,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        try {
            boolean approved = charge(event.bookingId());
            if (approved) {
                confirmationService.confirm(event.bookingId());
            } else {
                confirmationService.failAndCompensate(event.bookingId(), "Payment declined (simulated)");
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            handleInfraFailure(event, message, channel, deliveryTag, ex);
        }
    }

    private boolean charge(Long bookingId) {
        try {
            return paymentGatewaySimulator.charge(bookingId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating confirmation work", e);
        }
    }

    private void handleInfraFailure(
            BookingCreatedEvent event, Message message, Channel channel, long deliveryTag, Exception ex)
            throws IOException {
        int retryCount = retryCountOf(message);
        if (retryCount < maxRetries) {
            log.warn(
                    "Confirmation processing failed for bookingId={}, retry {}/{}",
                    event.bookingId(),
                    retryCount + 1,
                    maxRetries,
                    ex);
            republishWithIncrementedRetryCount(event, retryCount);
            channel.basicAck(deliveryTag, false);
        } else {
            log.error(
                    "Confirmation processing failed for bookingId={} after {} retries, sending to DLQ",
                    event.bookingId(),
                    maxRetries,
                    ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private int retryCountOf(Message message) {
        Object header = message.getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER);
        return header instanceof Number number ? number.intValue() : 0;
    }

    private void republishWithIncrementedRetryCount(BookingCreatedEvent event, int currentRetryCount) {
        MessagePostProcessor addRetryHeader =
                msg -> {
                    msg.getMessageProperties().setHeader(RETRY_COUNT_HEADER, currentRetryCount + 1);
                    return msg;
                };
        rabbitTemplate.convertAndSend(BOOKING_EXCHANGE, BOOKING_CREATED_ROUTING_KEY, event, addRetryHeader);
    }
}
