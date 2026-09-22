-- Run after the k6 load test against the same Postgres instance to confirm no overselling
-- occurred: reserved_count (bookings that hold a seat) must exactly equal seats consumed
-- from the counter, and must never exceed total_seats.
--
--   docker-compose exec postgres psql -U booktickets -d booktickets -v event_id=1 -f loadtest/verify-no-oversell.sql

SELECT
    e.id,
    e.total_seats,
    e.available_seats,
    (SELECT COUNT(*) FROM bookings b WHERE b.event_id = e.id AND b.status IN ('PENDING', 'CONFIRMED')) AS reserved_count,
    (e.total_seats - e.available_seats) AS seats_consumed,
    CASE
        WHEN (e.total_seats - e.available_seats) = (SELECT COUNT(*) FROM bookings b WHERE b.event_id = e.id AND b.status IN ('PENDING', 'CONFIRMED'))
             AND e.available_seats >= 0
        THEN 'OK: no overselling detected'
        ELSE 'FAIL: inventory and booking counts disagree - investigate'
    END AS verdict
FROM events e
WHERE e.id = :event_id;
