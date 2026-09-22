package com.bookings.booktickets.service.lock;

import java.util.Optional;

/**
 * Abstraction over the Redis coordination lock so tests can swap in a {@link NoOpLockService}
 * and prove that overselling is still prevented by the Postgres transaction alone - the lock
 * is a throughput optimization, not the correctness mechanism.
 */
public interface DistributedLockService {

    /** Empty if the lock could not be acquired within {@code waitSeconds}. */
    Optional<LockHandle> tryAcquire(String key, long waitSeconds, long leaseSeconds) throws InterruptedException;

    @FunctionalInterface
    interface LockHandle {
        void release();
    }
}
