package com.bookings.booktickets.service;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.domain.BookingStatus;
import com.bookings.booktickets.repository.BookingRepository;
import com.bookings.booktickets.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationService.class);

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;

    public BookingConfirmationService(BookingRepository bookingRepository, EventRepository eventRepository) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Returns false if the booking was already resolved (redelivery / duplicate message),
     * so the caller can treat it as a no-op instead of double-processing.
     */
    @Transactional
    public boolean confirm(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING) {
            log.info("Skipping confirm for bookingId={}, not in PENDING state (possible redelivery)", bookingId);
            return false;
        }
        booking.markConfirmed();
        bookingRepository.save(booking);
        return true;
    }

    /**
     * Marks the booking FAILED and releases its held inventory back to the event so a
     * declined/failed confirmation doesn't permanently shrink capacity.
     */
    @Transactional
    public boolean failAndCompensate(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING) {
            log.info("Skipping fail-compensate for bookingId={}, not in PENDING state", bookingId);
            return false;
        }
        booking.markFailed(reason);
        bookingRepository.save(booking);
        eventRepository.releaseSeats(booking.getEventId(), booking.getSeatsRequested());
        return true;
    }
}
