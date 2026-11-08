ALTER TABLE products
    ADD CONSTRAINT uq_products_id_vendor UNIQUE (id, vendor_id);

CREATE TABLE product_inventory (
    product_id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL,
    on_hand_quantity BIGINT NOT NULL DEFAULT 0,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_inventory_product_vendor
        FOREIGN KEY (product_id, vendor_id) REFERENCES products (id, vendor_id),
    CONSTRAINT ck_inventory_on_hand_non_negative CHECK (on_hand_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_not_over_on_hand CHECK (reserved_quantity <= on_hand_quantity)
);

INSERT INTO product_inventory (product_id, vendor_id)
SELECT id, vendor_id FROM products;

CREATE INDEX ix_product_inventory_vendor ON product_inventory (vendor_id, updated_at DESC);

CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES app_users(id),
    quantity_delta BIGINT NOT NULL,
    quantity_before BIGINT NOT NULL,
    quantity_after BIGINT NOT NULL,
    reason VARCHAR(24) NOT NULL,
    version_after BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_inventory_adjustment_product_vendor
        FOREIGN KEY (product_id, vendor_id) REFERENCES products (id, vendor_id),
    CONSTRAINT ck_inventory_adjustment_delta_non_zero CHECK (quantity_delta <> 0),
    CONSTRAINT ck_inventory_adjustment_quantities_non_negative
        CHECK (quantity_before >= 0 AND quantity_after >= 0),
    CONSTRAINT ck_inventory_adjustment_arithmetic
        CHECK (quantity_before + quantity_delta = quantity_after),
    CONSTRAINT ck_inventory_adjustment_reason
        CHECK (reason IN ('RESTOCK', 'CORRECTION', 'DAMAGE', 'RETURN'))
);

CREATE INDEX ix_inventory_adjustments_vendor_created
    ON inventory_adjustments (vendor_id, created_at DESC);
CREATE INDEX ix_inventory_adjustments_product_created
    ON inventory_adjustments (product_id, created_at DESC);
