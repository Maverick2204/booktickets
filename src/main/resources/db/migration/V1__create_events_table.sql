CREATE TABLE events (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255)   NOT NULL,
    venue            VARCHAR(255),
    event_time       TIMESTAMPTZ    NOT NULL,
    total_seats      INTEGER        NOT NULL,
    available_seats  INTEGER        NOT NULL,
    price_cents      BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_available_seats_nonnegative CHECK (available_seats >= 0),
    CONSTRAINT chk_available_seats_le_total CHECK (available_seats <= total_seats),
    CONSTRAINT chk_total_seats_positive CHECK (total_seats > 0)
);

CREATE INDEX idx_events_event_time ON events (event_time);
