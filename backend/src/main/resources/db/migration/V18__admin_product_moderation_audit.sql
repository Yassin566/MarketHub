CREATE TABLE admin_product_moderation_events (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    actor_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    previous_status VARCHAR(20) NOT NULL,
    resulting_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_product_moderation_status
        CHECK (previous_status <> resulting_status)
);

CREATE INDEX ix_admin_product_moderation_product_created
    ON admin_product_moderation_events(product_id, created_at DESC, id DESC);
