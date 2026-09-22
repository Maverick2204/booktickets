package com.bookings.booktickets.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bookings.booktickets.domain.BookingStatus;
import com.bookings.booktickets.domain.Event;
import com.bookings.booktickets.service.EventService;
import com.bookings.booktickets.support.AbstractFullStackIntegrationTest;
import com.bookings.booktickets.web.dto.BookingResponse;
import com.bookings.booktickets.web.dto.CreateBookingRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end proof of the async confirmation flow: create a booking over HTTP, then poll
 * until the RabbitMQ consumer has processed it and flipped it from PENDING to CONFIRMED.
 */
class BookingControllerIT extends AbstractFullStackIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventService eventService;

    @LocalServerPort
    private int port;

    private Long eventId;

    @BeforeEach
    void seedEvent() {
        Event event = eventService.createEvent(
                "Controller IT Event", "Test Venue", Instant.now().plus(1, ChronoUnit.DAYS), 100, 5000L);
        eventId = event.getId();
    }

    @Test
    void createBookingThenPollsUntilConfirmed() {
        CreateBookingRequest request = new CreateBookingRequest(eventId, "user-42", 2);

        ResponseEntity<BookingResponse> createResponse =
                restTemplate.postForEntity(url("/api/bookings"), request, BookingResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BookingResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo(BookingStatus.PENDING);

        await().atMost(10, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            ResponseEntity<BookingResponse> pollResponse =
                    restTemplate.getForEntity(url("/api/bookings/" + created.id()), BookingResponse.class);
            assertThat(pollResponse.getBody()).isNotNull();
            assertThat(pollResponse.getBody().status()).isEqualTo(BookingStatus.CONFIRMED);
        });
    }

    @Test
    void rejectsBookingWhenInventoryExhausted() {
        CreateBookingRequest first = new CreateBookingRequest(eventId, "user-a", 100);
        ResponseEntity<BookingResponse> firstResponse =
                restTemplate.postForEntity(url("/api/bookings"), first, BookingResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        CreateBookingRequest second = new CreateBookingRequest(eventId, "user-b", 1);
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(url("/api/bookings"), second, String.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
