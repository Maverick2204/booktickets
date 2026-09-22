package com.bookings.booktickets.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookings.booktickets.config.RabbitMqConfig;
import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.domain.BookingStatus;
import com.bookings.booktickets.domain.Event;
import com.bookings.booktickets.repository.BookingRepository;
import com.bookings.booktickets.repository.EventRepository;
import com.bookings.booktickets.service.BookingService;
import com.bookings.booktickets.service.EventService;
import com.bookings.booktickets.support.AbstractFullStackIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class BookingConfirmationListenerIT extends AbstractFullStackIntegrationTest {

    @MockBean
    private PaymentGatewaySimulator paymentGatewaySimulator;

    @Autowired
    private EventService eventService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void businessDeclineMarksBookingFailedAndReleasesInventory() throws Exception {
        when(paymentGatewaySimulator.charge(any())).thenReturn(false);

        Event event = eventService.createEvent(
                "Decline Test Event", "Test Venue", Instant.now().plus(1, ChronoUnit.DAYS), 10, 0L);
        Booking booking = bookingService.createBooking(event.getId(), "user-decline", 3);

        await().atMost(10, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.FAILED);
            assertThat(refreshed.getFailureReason()).isNotBlank();
        });

        Event refreshedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(refreshedEvent.getAvailableSeats()).isEqualTo(10);
    }

    @Test
    void infraFailureExhaustsRetriesAndLandsInDeadLetterQueue() throws Exception {
        when(paymentGatewaySimulator.charge(any())).thenThrow(new RuntimeException("simulated infra outage"));

        Event event = eventService.createEvent(
                "Infra Failure Test Event", "Test Venue", Instant.now().plus(1, ChronoUnit.DAYS), 10, 0L);
        bookingService.createBooking(event.getId(), "user-infra-failure", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(rabbitTemplate.receive(RabbitMqConfig.CONFIRMATION_DLQ))
                        .isNotNull());
    }
}
