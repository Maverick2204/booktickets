package com.bookings.booktickets.web.dto;

import com.bookings.booktickets.domain.Event;
import java.time.Instant;

public record EventResponse(
        Long id,
        String name,
        String venue,
        Instant eventTime,
        int totalSeats,
        int availableSeats,
        long priceCents) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getEventTime(),
                event.getTotalSeats(),
                event.getAvailableSeats(),
                event.getPriceCents());
    }
}
