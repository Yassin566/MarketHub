CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL REFERENCES app_users(id),
    product_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reservation_product_vendor
        FOREIGN KEY (product_id, vendor_id) REFERENCES products (id, vendor_id),
    CONSTRAINT ck_inventory_reservation_status
        CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'CONSUMED'))
);

CREATE INDEX ix_inventory_reservations_customer
    ON inventory_reservations (customer_user_id, created_at DESC);
CREATE INDEX ix_inventory_reservations_expiry
    ON inventory_reservations (expires_at, id) WHERE status = 'ACTIVE';
CREATE INDEX ix_inventory_reservations_product
    ON inventory_reservations (product_id, status);
