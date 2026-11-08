CREATE TABLE vendor_order_shipments (
    id UUID PRIMARY KEY,
    vendor_order_id UUID NOT NULL UNIQUE REFERENCES vendor_orders(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    carrier VARCHAR(80) NOT NULL,
    tracking_number VARCHAR(120) NOT NULL,
    tracking_url VARCHAR(2048),
    shipped_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    estimated_delivery_date DATE,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_shipment_tracking UNIQUE (carrier, tracking_number)
);
CREATE INDEX ix_vendor_shipments_vendor_created
    ON vendor_order_shipments(vendor_id, created_at DESC);
