package com.bookings.booktickets.web.dto;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.domain.BookingStatus;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long eventId,
        String userId,
        int seatsRequested,
        BookingStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getUserId(),
                booking.getSeatsRequested(),
                booking.getStatus(),
                booking.getFailureReason(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }
}
