CREATE TABLE app_users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT ck_app_users_role CHECK (role IN ('CUSTOMER','VENDOR','ADMIN','SUPER_ADMIN')),
 CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE','LOCKED','DISABLED'))
);
CREATE INDEX ix_app_users_status ON app_users(status);
ALTER TABLE vendors ADD CONSTRAINT fk_vendors_owner_user FOREIGN KEY (owner_user_id) REFERENCES app_users(id);
CREATE UNIQUE INDEX ux_vendors_owner_user ON vendors(owner_user_id);
CREATE TABLE refresh_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), token_hash VARCHAR(128) NOT NULL UNIQUE,
 family_id UUID NOT NULL, expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens(family_id);
