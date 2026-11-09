# Product review moderation

## Admin endpoints

All endpoints require the `ADMIN` or `SUPER_ADMIN` role.

- `GET /api/v1/admin/reviews?published=false&rating=1&productId=<uuid>&limit=20&offset=0` lists reviews, optionally filtered by publication state, rating, and product. Omit `published` to include both states.
- `PATCH /api/v1/admin/reviews/{reviewId}/publication` accepts `{"published":true}` or `{"published":false}`.

The authenticated administrator ID is taken from the security principal, never from the request body. Repeating the current publication state is idempotent and does not create a duplicate audit event. A real state transition and its audit event are committed in one transaction.

## Audit and public visibility

Every visibility transition is recorded in `product_review_moderation_events` with review, administrator, action, and timestamp. Reviews are retained when unpublished. Public review listing and catalogue rating aggregates already filter on `is_published = TRUE`, so unpublished reviews are excluded from both immediately after the transaction commits.
