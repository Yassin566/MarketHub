# Product reviews API

This phase establishes the persistence contract for verified-purchase product reviews. The HTTP endpoints are implemented in the next backend increment after the existing order-line and authentication contracts are wired into the review service.

## Data model

Migration `V16__product_reviews.sql` creates `product_reviews` with:

- A product and customer owner, both linked to existing records.
- The originating customer order, retained as purchase provenance.
- A required integer rating from 1 through 5.
- Optional review text up to 2,000 characters.
- Publication state and creation/update timestamps.
- A database-enforced one-review-per-customer-per-product rule.

Reviews cascade when a product or customer is removed. The originating order is restricted from deletion while a review references it, preserving the audit trail.

## API contract for the service layer

All customer write operations must derive the customer ID from the authenticated principal; clients must never submit an owner ID.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/products/{productId}/reviews?limit=20&offset=0` | Public, paginated published reviews with average rating and total count |
| POST | `/api/v1/customer/products/{productId}/reviews` | Create a verified-purchase review |
| PUT | `/api/v1/customer/reviews/{reviewId}` | Edit the authenticated customer's review |
| DELETE | `/api/v1/customer/reviews/{reviewId}` | Delete the authenticated customer's review |

## Required business rules

1. A customer may review a product only when an order line for that product belongs to the customer and the order has reached a fulfilled/delivered state.
2. The order ID supplied to the service must be verified against the authenticated customer and product; never trust client-supplied purchase provenance.
3. Enforce one review per customer/product in both service logic and the unique database constraint. Translate duplicate-key races into HTTP 409.
4. Validate rating 1–5, review text length at most 2,000 characters, and pagination bounds.
5. Public queries must include only published reviews and return newest first.
6. Edit/delete queries must include both review ID and authenticated customer ID.
7. Aggregate average and count must be calculated from published reviews only.

## Verification checklist

- Migration applies on PostgreSQL 17.
- Invalid ratings and duplicate customer/product reviews are rejected.
- Non-purchasers and customers with non-fulfilled orders cannot create reviews.
- Customers cannot edit or delete another customer's review.
- Anonymous users can read published reviews but cannot write reviews.
- Pagination and aggregate values remain correct when a review is edited or deleted.
