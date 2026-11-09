# Product reviews API

## Endpoints

- `GET /api/v1/products/{productId}/reviews?limit=20&offset=0` — public paginated published reviews, average rating, and review count.
- `POST /api/v1/customer/products/{productId}/reviews` — customer creates a review.
- `PUT /api/v1/customer/reviews/{reviewId}` — customer edits their own review.
- `DELETE /api/v1/customer/reviews/{reviewId}` — customer deletes their own review.

Write endpoints require the CUSTOMER role. The customer ID is always taken from the authenticated `MarketHubPrincipal`; request bodies cannot select an owner or purchase order.

## Verified purchase rule

A review can be created only if the authenticated customer owns an order containing the product and the corresponding vendor order has status `FULFILLED`. The service selects the qualifying order itself. A client cannot provide or forge the order ID.

## Validation and privacy

- Rating must be an integer from 1 to 5.
- Review text is optional and limited to 2,000 characters.
- Page size is 1–100 and offset must be non-negative.
- Public results contain published reviews only, newest first.
- Update and delete operations scope the SQL statement to both review ID and authenticated customer ID.
- The database unique constraint enforces one review per customer/product, including concurrent requests. Duplicate attempts are rejected by PostgreSQL's unique constraint.

The migration retains the purchase order reference and indexes the public product feed and customer history.
