package com.bookings.booktickets.service;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.exception.InsufficientInventoryException;
import com.bookings.booktickets.messaging.BookingCreatedEvent;
import com.bookings.booktickets.messaging.BookingEventPublisher;
import com.bookings.booktickets.repository.BookingRepository;
import com.bookings.booktickets.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The actual correctness guarantee for "no overselling": a single atomic conditional UPDATE
 * (see {@link EventRepository#reserveSeats}) that only succeeds while enough seats remain,
 * followed by the booking insert, all inside one transaction. This holds even if the
 * surrounding Redis coordination lock in {@link BookingService} were absent entirely - the
 * lock only reduces contention/retries, it is not what prevents overselling.
 *
 * <p>Lives in its own Spring bean (rather than a private method on {@link BookingService}) so
 * the {@code @Transactional} annotation goes through the Spring AOP proxy correctly; calling
 * it via {@code this.reserveAndCreateBooking(...)} from within the same class would silently
 * bypass the proxy and run without a transaction.
 */
@Service
public class BookingReservationWorker {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public BookingReservationWorker(
            EventRepository eventRepository, BookingRepository bookingRepository, BookingEventPublisher eventPublisher) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Booking reserveAndCreateBooking(Long eventId, String userId, int seats) {
        int updated = eventRepository.reserveSeats(eventId, seats);
        if (updated == 0) {
            throw new InsufficientInventoryException(eventId, seats);
        }

        Booking booking = bookingRepository.save(new Booking(eventId, userId, seats));

        // Published now (while this transaction is still open) so the @TransactionalEventListener
        // in BookingEventPublisher binds to THIS transaction's AFTER_COMMIT phase. Publishing
        // after this method returns would be too late - there'd be no active transaction to bind to.
        eventPublisher.publishAfterCommit(
                new BookingCreatedEvent(booking.getId(), eventId, userId, seats, booking.getCreatedAt()));

        return booking;
    }
}
