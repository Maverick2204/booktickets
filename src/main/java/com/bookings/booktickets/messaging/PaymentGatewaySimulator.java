package com.bookings.booktickets.messaging;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real payment/inventory-finalization call. Adds a small artificial delay
 * and can be configured to fail a fraction of the time so the FAILED / dead-letter path is
 * exercised during demos and tests, rather than only ever taking the happy path.
 */
@Component
public class PaymentGatewaySimulator {

    private final long minDelayMs;
    private final long maxDelayMs;
    private final double simulatedFailureRate;

    public PaymentGatewaySimulator(
            @Value("${booking.confirmation.min-delay-ms}") long minDelayMs,
            @Value("${booking.confirmation.max-delay-ms}") long maxDelayMs,
            @Value("${booking.confirmation.simulated-failure-rate}") double simulatedFailureRate) {
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.simulatedFailureRate = simulatedFailureRate;
    }

    public boolean charge(Long bookingId) throws InterruptedException {
        long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
        Thread.sleep(delay);
        return ThreadLocalRandom.current().nextDouble() >= simulatedFailureRate;
    }
}
