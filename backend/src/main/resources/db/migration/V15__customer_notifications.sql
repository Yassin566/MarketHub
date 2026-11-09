CREATE TABLE customer_notifications (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL REFERENCES app_users(id),
    order_id UUID REFERENCES customer_orders(id),
    event_key VARCHAR(160) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_customer_notification_event UNIQUE (customer_user_id, event_key)
);

CREATE INDEX ix_customer_notifications_customer_created
    ON customer_notifications(customer_user_id, created_at DESC, id DESC);
CREATE INDEX ix_customer_notifications_customer_unread
    ON customer_notifications(customer_user_id, created_at DESC)
    WHERE read_at IS NULL;
