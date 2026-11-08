ALTER TABLE payments DROP CONSTRAINT ck_payment_status;
ALTER TABLE payments ADD CONSTRAINT ck_payment_status CHECK (
    status IN ('REQUESTED','PROCESSING','SUCCEEDED','FAILED','CANCELLED','EXPIRED','REFUNDED','REQUIRES_REVIEW')
);

CREATE TABLE payment_provider_events (
    id UUID PRIMARY KEY,
    provider_code VARCHAR(40) NOT NULL,
    event_id VARCHAR(120) NOT NULL,
    payment_id UUID NOT NULL REFERENCES payments(id),
    event_type VARCHAR(80) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    outcome VARCHAR(60),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_payment_provider_event UNIQUE (provider_code, event_id)
);

CREATE INDEX ix_payment_provider_events_payment
    ON payment_provider_events(payment_id, received_at DESC);
