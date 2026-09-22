package com.bookings.booktickets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookings.booktickets.domain.Booking;
import com.bookings.booktickets.exception.EventNotFoundException;
import com.bookings.booktickets.exception.LockAcquisitionException;
import com.bookings.booktickets.repository.EventRepository;
import com.bookings.booktickets.service.lock.DistributedLockService;
import com.bookings.booktickets.service.lock.DistributedLockService.LockHandle;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingServiceTest {

    private EventRepository eventRepository;
    private BookingReservationWorker reservationWorker;
    private DistributedLockService lockService;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        reservationWorker = mock(BookingReservationWorker.class);
        lockService = mock(DistributedLockService.class);
        bookingService = new BookingService(lockService, reservationWorker, eventRepository, 2, 5);
    }

    @Test
    void throwsWhenEventDoesNotExist() throws Exception {
        when(eventRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(1L, "user-1", 1))
                .isInstanceOf(EventNotFoundException.class);

        verify(lockService, never()).tryAcquire(any(), anyLong(), anyLong());
    }

    @Test
    void throwsLockAcquisitionExceptionWhenLockNotAcquired() throws Exception {
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(lockService.tryAcquire("lock:event:1", 2, 5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(1L, "user-1", 1))
                .isInstanceOf(LockAcquisitionException.class);

        verify(reservationWorker, never()).reserveAndCreateBooking(any(), any(), anyInt());
    }

    @Test
    void reservesBookingAndReleasesLockOnSuccess() throws Exception {
        LockHandle lockHandle = mock(LockHandle.class);
        Booking booking = new Booking(1L, "user-1", 2);
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(lockService.tryAcquire("lock:event:1", 2, 5)).thenReturn(Optional.of(lockHandle));
        when(reservationWorker.reserveAndCreateBooking(1L, "user-1", 2)).thenReturn(booking);

        Booking result = bookingService.createBooking(1L, "user-1", 2);

        assertThat(result).isSameAs(booking);
        verify(reservationWorker, times(1)).reserveAndCreateBooking(1L, "user-1", 2);
        verify(lockHandle, times(1)).release();
    }

    @Test
    void releasesLockEvenWhenReservationThrows() throws Exception {
        LockHandle lockHandle = mock(LockHandle.class);
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(lockService.tryAcquire("lock:event:1", 2, 5)).thenReturn(Optional.of(lockHandle));
        when(reservationWorker.reserveAndCreateBooking(1L, "user-1", 1))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> bookingService.createBooking(1L, "user-1", 1))
                .isInstanceOf(RuntimeException.class);

        verify(lockHandle, times(1)).release();
    }
}
