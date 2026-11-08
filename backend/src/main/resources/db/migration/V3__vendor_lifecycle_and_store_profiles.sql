ALTER TABLE vendors
  ADD COLUMN reviewed_at TIMESTAMPTZ,
  ADD COLUMN reviewed_by UUID REFERENCES app_users(id),
  ADD COLUMN rejection_reason VARCHAR(500);
ALTER TABLE vendors ADD CONSTRAINT ck_vendors_status
  CHECK (status IN ('PENDING','APPROVED','REJECTED','SUSPENDED'));
CREATE INDEX ix_vendors_status_created ON vendors(status, created_at);

CREATE TABLE store_profiles (
  id UUID PRIMARY KEY,
  vendor_id UUID NOT NULL UNIQUE REFERENCES vendors(id),
  store_name VARCHAR(160) NOT NULL,
  slug VARCHAR(180) NOT NULL UNIQUE,
  description VARCHAR(2000),
  logo_url VARCHAR(1000),
  support_email VARCHAR(254),
  support_phone VARCHAR(40),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE products
  ADD COLUMN description VARCHAR(4000),
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN archived_at TIMESTAMPTZ;
CREATE INDEX ix_products_vendor_created ON products(vendor_id, created_at DESC);
