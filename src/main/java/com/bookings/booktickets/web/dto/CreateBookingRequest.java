package com.bookings.booktickets.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code userId} is a caller-supplied identifier only - this demo has no authentication, so
 * it identifies who is asking but does not verify that they are who they claim to be.
 */
public record CreateBookingRequest(@NotNull Long eventId, @NotBlank String userId, @Positive Integer seats) {}
