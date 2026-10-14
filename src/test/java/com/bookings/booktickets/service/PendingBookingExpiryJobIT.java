package com.bookings.booktickets.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.domain.BookingStatus;
import com.bookings.booktickets.domain.Event;
import com.bookings.booktickets.repository.BookingRepository;
import com.bookings.booktickets.repository.EventRepository;
import com.bookings.booktickets.support.AbstractPostgresRedisIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RabbitTemplate is mocked in this base class, so bookings never get confirmed - i.e. stuck in PENDING. */
class PendingBookingExpiryJobIT extends AbstractPostgresRedisIntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PendingBookingExpiryJob expiryJob;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void stuckPendingBookingIsExpiredAndSeatsReleasedExactlyOnce() {
        Event event = eventService.createEvent(
                "Expiry Test Event", "Test Venue", Instant.now().plus(1, ChronoUnit.DAYS), 10, 0L);
        Booking booking = bookingService.createBooking(event.getId(), "user-stuck", 4);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getAvailableSeats()).isEqualTo(6);

        Instant cutoff = Instant.now().plusSeconds(1);
        assertThat(expiryJob.expirePendingCreatedBefore(cutoff)).isGreaterThanOrEqualTo(1);
        // Second sweep must not release the same seats again.
        expiryJob.expirePendingCreatedBefore(cutoff);

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getAvailableSeats()).isEqualTo(10);
    }

    @Test
    void recentPendingBookingIsLeftAlone() {
        Event event = eventService.createEvent(
                "Fresh Booking Event", "Test Venue", Instant.now().plus(1, ChronoUnit.DAYS), 10, 0L);
        Booking booking = bookingService.createBooking(event.getId(), "user-fresh", 2);

        expiryJob.expirePendingCreatedBefore(Instant.now().minus(15, ChronoUnit.MINUTES));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getAvailableSeats()).isEqualTo(8);
    }
}
