CREATE TABLE payment_manual_resolutions (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
    actor_user_id UUID NOT NULL REFERENCES app_users(id),
    decision VARCHAR(24) NOT NULL CHECK (decision IN ('CONFIRM_SUCCEEDED', 'CONFIRM_FAILED')),
    previous_payment_status VARCHAR(24) NOT NULL,
    resulting_payment_status VARCHAR(24) NOT NULL CHECK (resulting_payment_status IN ('SUCCEEDED', 'FAILED')),
    evidence_reference VARCHAR(300) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_manual_resolution_previous_status
        CHECK (previous_payment_status = 'REQUIRES_REVIEW')
);

CREATE INDEX ix_payment_manual_resolutions_actor_created
    ON payment_manual_resolutions(actor_user_id, created_at DESC);
