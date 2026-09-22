package com.bookings.booktickets.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookings.booktickets.domain.BookingStatus;
import com.bookings.booktickets.domain.Event;
import com.bookings.booktickets.exception.InsufficientInventoryException;
import com.bookings.booktickets.exception.LockAcquisitionException;
import com.bookings.booktickets.repository.BookingRepository;
import com.bookings.booktickets.repository.EventRepository;
import com.bookings.booktickets.service.BookingService;
import com.bookings.booktickets.service.EventService;
import com.bookings.booktickets.support.AbstractPostgresRedisIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Same proof as {@link BookingConcurrencyTest}, but with the Redis coordination lock disabled
 * ({@code booking.lock.enabled=false}, activating {@link com.bookings.booktickets.service.lock.NoOpLockService}).
 * This empirically demonstrates the defense-in-depth claim: the Postgres transaction's atomic
 * conditional UPDATE prevents overselling entirely on its own, with no locking whatsoever. In
 * this mode more requests may race onto the database simultaneously (worse for throughput /
 * contention), but correctness - the exact same 50-succeed/150-rejected outcome - still holds.
 */
@TestPropertySource(properties = "booking.lock.enabled=false")
class BookingConcurrencyWithoutRedisLockTest extends AbstractPostgresRedisIntegrationTest {

    private static final int SEAT_CAPACITY = 50;
    private static final int TOTAL_ATTEMPTS = 200;

    @Autowired
    private EventService eventService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    @Timeout(60)
    void databaseAloneNeverOversellsCapacityEvenWithoutTheLock() throws Exception {
        Event event = eventService.createEvent(
                "No-Lock Concurrency Test Event",
                "Test Venue",
                Instant.now().plus(1, ChronoUnit.DAYS),
                SEAT_CAPACITY,
                0L);
        Long eventId = event.getId();

        CountDownLatch readyLatch = new CountDownLatch(TOTAL_ATTEMPTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        List<Throwable> unexpectedErrors = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < TOTAL_ATTEMPTS; i++) {
                String userId = "user-" + i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        bookingService.createBooking(eventId, userId, 1);
                        successCount.incrementAndGet();
                    } catch (InsufficientInventoryException | LockAcquisitionException expected) {
                        rejectedCount.incrementAndGet();
                    } catch (Throwable unexpected) {
                        synchronized (unexpectedErrors) {
                            unexpectedErrors.add(unexpected);
                        }
                    }
                }));
            }

            readyLatch.await(10, TimeUnit.SECONDS);
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(45, TimeUnit.SECONDS);
            }
        }

        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(SEAT_CAPACITY);
        assertThat(rejectedCount.get()).isEqualTo(TOTAL_ATTEMPTS - SEAT_CAPACITY);

        Event refreshedEvent = eventRepository.findById(eventId).orElseThrow();
        assertThat(refreshedEvent.getAvailableSeats()).isZero();

        long reservedBookingCount =
                bookingRepository.countByEventIdAndStatusIn(eventId, List.of(BookingStatus.PENDING));
        assertThat(reservedBookingCount).isEqualTo(SEAT_CAPACITY);
    }
}
