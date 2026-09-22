package com.bookings.booktickets.service;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.exception.EventNotFoundException;
import com.bookings.booktickets.exception.LockAcquisitionException;
import com.bookings.booktickets.repository.EventRepository;
import com.bookings.booktickets.service.lock.DistributedLockService;
import com.bookings.booktickets.service.lock.DistributedLockService.LockHandle;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Owns the distributed lock used to serialize concurrent booking attempts against the same
 * event. This is a throughput/coordination optimization - it reduces DB contention and
 * retries when many requests race for the same event, especially across multiple app
 * instances - but it is deliberately NOT the thing that guarantees correctness. See
 * {@link BookingReservationWorker} for the actual overselling-prevention guarantee, which
 * holds independently of this lock (proven by running with {@code booking.lock.enabled=false},
 * see {@link com.bookings.booktickets.service.lock.NoOpLockService}).
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String LOCK_KEY_PREFIX = "lock:event:";

    private final DistributedLockService lockService;
    private final BookingReservationWorker reservationWorker;
    private final EventRepository eventRepository;
    private final long waitSeconds;
    private final long leaseSeconds;

    public BookingService(
            DistributedLockService lockService,
            BookingReservationWorker reservationWorker,
            EventRepository eventRepository,
            @Value("${booking.lock.wait-seconds}") long waitSeconds,
            @Value("${booking.lock.lease-seconds}") long leaseSeconds) {
        this.lockService = lockService;
        this.reservationWorker = reservationWorker;
        this.eventRepository = eventRepository;
        this.waitSeconds = waitSeconds;
        this.leaseSeconds = leaseSeconds;
    }

    public Booking createBooking(Long eventId, String userId, int seats) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

        Optional<LockHandle> lockHandle;
        try {
            lockHandle = lockService.tryAcquire(LOCK_KEY_PREFIX + eventId, waitSeconds, leaseSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(eventId);
        }

        if (lockHandle.isEmpty()) {
            log.warn("Failed to acquire booking lock for eventId={} within {}s", eventId, waitSeconds);
            throw new LockAcquisitionException(eventId);
        }

        try {
            return reservationWorker.reserveAndCreateBooking(eventId, userId, seats);
        } finally {
            lockHandle.get().release();
        }
    }
}
