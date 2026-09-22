package com.bookings.booktickets.exception;

public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(Long eventId, int requested) {
        super("Not enough seats available for event " + eventId + " (requested " + requested + ")");
    }
}
