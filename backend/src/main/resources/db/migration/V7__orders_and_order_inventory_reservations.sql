CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL REFERENCES app_users(id),
    idempotency_key UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL CHECK (subtotal >= 0),
    total NUMERIC(19,4) NOT NULL CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_order_idempotency UNIQUE (customer_user_id, idempotency_key),
    CONSTRAINT ck_customer_order_status CHECK (status IN ('PENDING_PAYMENT','PAID','CANCELLED','EXPIRED'))
);

CREATE TABLE vendor_orders (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL CHECK (subtotal >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_order_order_vendor UNIQUE (order_id, vendor_id),
    CONSTRAINT ck_vendor_order_status CHECK (status IN ('PENDING_PAYMENT','PAID','CANCELLED','EXPIRED','PROCESSING','FULFILLED'))
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id),
    vendor_order_id UUID NOT NULL REFERENCES vendor_orders(id),
    product_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity BETWEEN 1 AND 1000),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    currency VARCHAR(3) NOT NULL,
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_order_item_product_vendor FOREIGN KEY (product_id, vendor_id)
        REFERENCES products (id, vendor_id)
);

CREATE INDEX ix_customer_orders_customer_created ON customer_orders(customer_user_id, created_at DESC);
CREATE INDEX ix_vendor_orders_vendor_created ON vendor_orders(vendor_id, created_at DESC);
CREATE INDEX ix_order_items_order ON order_items(order_id, id);
CREATE INDEX ix_order_items_vendor_order ON order_items(vendor_order_id, id);

ALTER TABLE inventory_reservations
    ADD COLUMN order_id UUID REFERENCES customer_orders(id);
CREATE UNIQUE INDEX uq_order_inventory_reservation_product
    ON inventory_reservations(order_id, product_id) WHERE order_id IS NOT NULL;
CREATE INDEX ix_inventory_reservations_order
    ON inventory_reservations(order_id, status) WHERE order_id IS NOT NULL;
