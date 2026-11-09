CREATE TABLE product_review_moderation_events (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES product_reviews(id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    action VARCHAR(20) NOT NULL CHECK (action IN ('PUBLISHED', 'UNPUBLISHED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_product_review_moderation_events_review_created
    ON product_review_moderation_events(review_id, created_at DESC, id DESC);
