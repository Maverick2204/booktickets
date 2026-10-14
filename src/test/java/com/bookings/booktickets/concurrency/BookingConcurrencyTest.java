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

/**
 * Proves the core guarantee: under 200 truly concurrent booking attempts against 50 seats, the
 * platform never oversells - exactly 50 bookings succeed, the rest are correctly rejected, and
 * the database ends up in a consistent state. Runs with the real Redis lock (via
 * {@code booking.lock.enabled=true}, the default). See {@link BookingConcurrencyWithoutRedisLockTest}
 * for the same proof with the lock disabled, showing the database layer alone is sufficient.
 */
class BookingConcurrencyTest extends AbstractPostgresRedisIntegrationTest {

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
    void concurrentBookingsNeverOversellCapacity() throws Exception {
        Event event = eventService.createEvent(
                "Concurrency Test Event", "Test Venue", Instant.now().plus(1, ChronoUnit.DAYS), SEAT_CAPACITY, 0L);
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
