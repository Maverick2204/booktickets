package com.bookings.booktickets.repository;

import com.bookings.booktickets.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Atomic, race-free reservation: succeeds (returns 1) only if enough seats are
     * still available at the moment the row is scanned/modified. Postgres takes an
     * implicit row-level lock for the duration of this statement, so this is the
     * single source of truth that prevents overselling even if the Redis
     * coordination lock is bypassed or fails.
     */
    @Modifying
    @Query("UPDATE Event e SET e.availableSeats = e.availableSeats - :seats "
            + "WHERE e.id = :eventId AND e.availableSeats >= :seats")
    int reserveSeats(@Param("eventId") Long eventId, @Param("seats") int seats);

    @Modifying
    @Query("UPDATE Event e SET e.availableSeats = e.availableSeats + :seats WHERE e.id = :eventId")
    int releaseSeats(@Param("eventId") Long eventId, @Param("seats") int seats);
}
