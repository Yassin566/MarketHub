CREATE TABLE vendor_order_status_history (
    id UUID PRIMARY KEY,
    vendor_order_id UUID NOT NULL REFERENCES vendor_orders(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    actor_user_id UUID NOT NULL REFERENCES app_users(id),
    previous_status VARCHAR(24) NOT NULL,
    new_status VARCHAR(24) NOT NULL,
    note VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_vendor_order_history_transition CHECK (
        (previous_status = 'PAID' AND new_status = 'PROCESSING')
        OR (previous_status = 'PROCESSING' AND new_status = 'FULFILLED')
    )
);
CREATE INDEX ix_vendor_order_history_order_created
    ON vendor_order_status_history(vendor_order_id, created_at DESC, id DESC);
