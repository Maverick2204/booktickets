CREATE TABLE bookings (
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT       NOT NULL REFERENCES events (id),
    user_id          VARCHAR(255) NOT NULL,
    seats_requested  INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    failure_reason   VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_seats_requested_positive CHECK (seats_requested > 0),
    CONSTRAINT chk_status_valid CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_bookings_event_id ON bookings (event_id);
CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_status ON bookings (status);
