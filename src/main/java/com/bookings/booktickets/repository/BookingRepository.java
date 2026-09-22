package com.bookings.booktickets.repository;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.domain.BookingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventId(Long eventId);

    long countByEventIdAndStatusIn(Long eventId, List<BookingStatus> statuses);
}
