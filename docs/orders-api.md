# Customer order creation

All endpoints require a customer access token. The customer identity is taken from the authenticated principal.

## Create an order from the current cart

POST /api/v1/customer/orders/checkout

Required header: \`Idempotency-Key: <UUID>\`

The endpoint validates the entire cart and, in one PostgreSQL transaction, creates a pending-payment parent order, one child order per vendor, immutable product/price line snapshots, and 15-minute inventory reservations. It then clears the cart. If any product is inactive, a vendor is not approved, currencies differ, or stock is insufficient, the transaction rolls back and no order or reservation is created.

Retrying the same request with the same customer and idempotency key returns the original order rather than creating a duplicate. Use a new UUID for a new checkout attempt.

The order response includes its current status, currency, subtotal/total, reservation expiry, and line snapshots.

## Read an order

GET /api/v1/customer/orders/<order-uuid>

Only the owning customer can read the order; another customer receives a not-found response.

## Important scope boundary

Orders remain \`PENDING_PAYMENT\`. This phase does not charge a payment method, mark orders paid, calculate shipping/tax/commission, or trigger fulfillment. Reservations expire after 15 minutes and release their stock automatically; the corresponding pending order becomes \`EXPIRED\`. Payment integration must later use an outbox and verified, idempotent provider webhooks, and must never hold database locks during remote calls.
