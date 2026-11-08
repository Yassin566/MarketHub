CREATE TABLE payment_reconciliation_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    outcome VARCHAR(60) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    observed_payment_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_reconciliation_attempt UNIQUE (payment_id, attempt_number)
);

CREATE INDEX ix_payment_reconciliation_attempts_created
    ON payment_reconciliation_attempts(created_at DESC, id DESC);
CREATE INDEX ix_payments_stale_processing
    ON payments(updated_at, id)
    WHERE status = 'PROCESSING';
