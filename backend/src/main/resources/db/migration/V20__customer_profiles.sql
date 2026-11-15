CREATE TABLE customer_profiles (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_profiles_first_name CHECK (length(trim(first_name)) > 0),
    CONSTRAINT ck_customer_profiles_last_name CHECK (length(trim(last_name)) > 0)
);
