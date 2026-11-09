CREATE TABLE admin_vendor_status_events (
 id UUID PRIMARY KEY, vendor_id UUID NOT NULL REFERENCES vendors(id),
 actor_user_id UUID NOT NULL REFERENCES app_users(id), previous_status VARCHAR(24) NOT NULL,
 resulting_status VARCHAR(24) NOT NULL, reason VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT ck_admin_vendor_status_transition CHECK (
 (previous_status='APPROVED' AND resulting_status='SUSPENDED') OR
 (previous_status='SUSPENDED' AND resulting_status='APPROVED'))
);
CREATE INDEX ix_admin_vendor_status_events_vendor_created ON admin_vendor_status_events(vendor_id,created_at DESC);
