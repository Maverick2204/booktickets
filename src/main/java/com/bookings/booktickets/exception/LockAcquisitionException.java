package com.bookings.booktickets.exception;

public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(Long eventId) {
        super("Could not acquire booking lock for event " + eventId + " in time, system is busy");
    }
}
