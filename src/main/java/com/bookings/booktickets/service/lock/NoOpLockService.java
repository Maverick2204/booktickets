package com.bookings.booktickets.service.lock;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Test-only stand-in that grants every lock request immediately. Used exclusively to prove
 * that {@link com.bookings.booktickets.service.BookingReservationWorker}'s Postgres
 * transaction independently prevents overselling even with zero lock coordination.
 */
@Service
@ConditionalOnProperty(name = "booking.lock.enabled", havingValue = "false")
public class NoOpLockService implements DistributedLockService {

    @Override
    public Optional<LockHandle> tryAcquire(String key, long waitSeconds, long leaseSeconds) {
        return Optional.of(() -> {});
    }
}
