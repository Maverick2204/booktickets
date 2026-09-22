package com.bookings.booktickets.service;

import com.bookings.booktickets.domain.Event;
import com.bookings.booktickets.exception.EventNotFoundException;
import com.bookings.booktickets.repository.EventRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(String name, String venue, Instant eventTime, int totalSeats, Long priceCents) {
        return eventRepository.save(new Event(name, venue, eventTime, totalSeats, priceCents));
    }

    public Event getEvent(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
