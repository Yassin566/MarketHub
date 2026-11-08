CREATE TABLE customer_carts (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL UNIQUE REFERENCES app_users(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_carts_status CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED'))
);
CREATE TABLE customer_cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES customer_carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_cart_item_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_customer_cart_item_quantity CHECK (quantity BETWEEN 1 AND 1000)
);
CREATE INDEX ix_customer_cart_items_cart ON customer_cart_items(cart_id, created_at, id);
CREATE INDEX ix_customer_cart_items_product ON customer_cart_items(product_id);
