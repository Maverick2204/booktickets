import http from "k6/http";
import { check } from "k6";

// Deliberately requests far more seats in aggregate than the event has, so the
// oversell-prevention path (409 responses once inventory is exhausted, or 503 under
// lock contention) is actually exercised under real concurrent load - not just the
// happy path. Run against the docker-compose stack:
//
//   1. docker-compose up -d
//   2. EVENT_ID=$(curl -s -X POST http://localhost:8080/api/events \
//        -H 'Content-Type: application/json' \
//        -d '{"name":"Load Test Event","venue":"Load Test Hall","eventTime":"2027-01-01T10:00:00Z","totalSeats":500,"priceCents":0}' \
//        | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
//   3. k6 run loadtest/k6/booking-load-test.js -e BASE_URL=http://localhost:8080 -e EVENT_ID=$EVENT_ID
//   4. psql against the compose Postgres with loadtest/verify-no-oversell.sql to confirm
//      total_seats - available_seats == reserved_count (no overselling occurred).

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EVENT_ID = __ENV.EVENT_ID;

export const options = {
    scenarios: {
        booking_rush: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "10s", target: 200 },
                { duration: "30s", target: 200 },
                { duration: "10s", target: 0 },
            ],
        },
    },
    thresholds: {
        // Informational starting point for this demo - tune after the first real run.
        http_req_duration: ["p(95)<500"],
    },
};

export default function () {
    if (!EVENT_ID) {
        throw new Error("EVENT_ID env var is required - seed an event first, see header comment");
    }

    const payload = JSON.stringify({
        eventId: Number(EVENT_ID),
        userId: `loadtest-user-${__VU}-${__ITER}`,
        seats: 1,
    });

    const res = http.post(`${BASE_URL}/api/bookings`, payload, {
        headers: { "Content-Type": "application/json" },
    });

    // 201 = booked, 409 = correctly rejected (sold out), 503 = correctly rejected (lock
    // contention backpressure). All three are the platform behaving correctly under load;
    // anything else is an actual failure.
    check(res, {
        "handled correctly (201/409/503)": (r) => [201, 409, 503].includes(r.status),
    });
}
