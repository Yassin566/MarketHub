CREATE TABLE vendors (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE products (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    price NUMERIC(19,4) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_products_vendor_status ON products(vendor_id, status);
