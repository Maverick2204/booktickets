package com.bookings.booktickets.messaging;

import java.io.Serializable;
import java.time.Instant;

public record BookingCreatedEvent(
        Long bookingId, Long eventId, String userId, int seatsRequested, Instant createdAt)
        implements Serializable {}
