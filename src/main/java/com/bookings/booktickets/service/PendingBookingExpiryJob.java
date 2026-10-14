package com.bookings.booktickets.service;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.domain.BookingStatus;
import com.bookings.booktickets.repository.BookingRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases seats held by bookings stuck in PENDING - e.g. the app crashed after commit but
 * before the booking.created message reached RabbitMQ, or the message was dead-lettered.
 * Without this, those seats would be gone for good. Safe to run on every instance at once:
 * {@link BookingConfirmationService#failAndCompensate} row-locks the booking and no-ops if
 * it's no longer PENDING.
 */
@Component
public class PendingBookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingBookingExpiryJob.class);

    private final BookingRepository bookingRepository;
    private final BookingConfirmationService confirmationService;
    private final Duration pendingTtl;

    public PendingBookingExpiryJob(
            BookingRepository bookingRepository,
            BookingConfirmationService confirmationService,
            @Value("${booking.expiry.pending-ttl}") Duration pendingTtl) {
        this.bookingRepository = bookingRepository;
        this.confirmationService = confirmationService;
        this.pendingTtl = pendingTtl;
    }

    @Scheduled(fixedDelayString = "${booking.expiry.sweep-interval-ms}")
    public void expireStaleBookings() {
        int expired = expirePendingCreatedBefore(Instant.now().minus(pendingTtl));
        if (expired > 0) {
            log.warn("Expired {} booking(s) stuck in PENDING longer than {}", expired, pendingTtl);
        }
    }

    int expirePendingCreatedBefore(Instant cutoff) {
        int expired = 0;
        for (Booking booking : bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)) {
            if (confirmationService.failAndCompensate(booking.getId(), "Expired: not confirmed in time")) {
                expired++;
            }
        }
        return expired;
    }
}
