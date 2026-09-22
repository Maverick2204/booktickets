package com.bookings.booktickets.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record CreateEventRequest(
        @NotBlank String name,
        String venue,
        @NotNull @Future Instant eventTime,
        @NotNull @Positive Integer totalSeats,
        @PositiveOrZero Long priceCents) {}
