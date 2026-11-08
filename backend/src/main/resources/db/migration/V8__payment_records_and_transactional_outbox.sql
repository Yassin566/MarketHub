CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id),
    customer_user_id UUID NOT NULL REFERENCES app_users(id),
    idempotency_key UUID NOT NULL,
    provider_code VARCHAR(40) NOT NULL DEFAULT 'UNCONFIGURED',
    provider_reference VARCHAR(180),
    status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_customer_idempotency UNIQUE (customer_user_id, idempotency_key),
    CONSTRAINT ck_payment_status CHECK (
        status IN ('REQUESTED','PROCESSING','SUCCEEDED','FAILED','CANCELLED','EXPIRED','REFUNDED')
    )
);

CREATE INDEX ix_payments_order_created ON payments(order_id, created_at DESC);
CREATE UNIQUE INDEX uq_payment_provider_reference
    ON payments(provider_code, provider_reference)
    WHERE provider_reference IS NOT NULL;
CREATE UNIQUE INDEX uq_payment_active_order
    ON payments(order_id)
    WHERE status IN ('REQUESTED','PROCESSING');

CREATE TABLE transactional_outbox (
    id UUID PRIMARY KEY,
    event_key UUID NOT NULL UNIQUE,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactional_outbox_status CHECK (
        status IN ('PENDING','PROCESSING','DELIVERED','DEAD')
    )
);

CREATE INDEX ix_transactional_outbox_dispatch
    ON transactional_outbox(status, available_at, created_at)
    WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX ix_transactional_outbox_aggregate
    ON transactional_outbox(aggregate_type, aggregate_id, created_at);
