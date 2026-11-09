CREATE TABLE product_reviews (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    customer_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES customer_orders(id) ON DELETE RESTRICT,
    rating SMALLINT NOT NULL,
    review_text VARCHAR(2000),
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_product_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_product_reviews_customer_product UNIQUE (customer_user_id, product_id)
);

CREATE INDEX ix_product_reviews_product_published_created
    ON product_reviews(product_id, created_at DESC, id DESC)
    WHERE is_published = TRUE;

CREATE INDEX ix_product_reviews_customer_created
    ON product_reviews(customer_user_id, created_at DESC);
