# Architecture decisions

MarketHub is a modular monolith. Modules own their domain model and persistence; cross-module communication should use public application interfaces or domain events.

## Multi-tenancy

Vendor-owned aggregates carry vendor_id. Every vendor operation derives vendor identity from the authenticated principal and checks ownership in the application service. Never trust a vendor ID supplied by the browser as proof of ownership.

## Checkout consistency

Order creation locks the customer's cart and product/inventory rows in stable product-ID order. It validates all lines and creates the parent order, per-vendor child orders, line-item snapshots, and expiring inventory reservations in one database transaction. A failed line rolls back the entire checkout. Idempotency keys prevent duplicate orders on client retries. Do not hold database locks during remote payment calls.

## Payment consistency

A customer payment request locks the parent order and its reservation rows, verifies that all stock reservations remain active, and writes the payment attempt and `PAYMENT_REQUESTED` outbox event in one transaction. Provider calls must be performed by an outbox dispatcher outside that transaction. Provider callbacks must be authenticated, deduplicated, and reconciled before any payment/order state transition. Late payment success after reservation expiry must not automatically mark an order paid; it requires an explicit compensation or manual-review policy.

## Transactional outbox delivery

Outbox claims use `FOR UPDATE SKIP LOCKED`, bounded batches, attempt limits, and expiring lease tokens. A worker must process events outside the claim transaction and use the lease token to complete or reschedule them. A stale worker cannot complete an event after another worker has reclaimed its lease. Provider-side idempotency remains mandatory because a worker can crash after a provider accepts a request but before the outbox row is acknowledged.

## Orders and money

A parent order represents the customer's unified purchase; child vendor orders snapshot line items and currency. Current order totals include product line items only. Taxes, shipping, discounts, commission, and payout snapshots must be added before production payment/fulfillment. Use NUMERIC/BigDecimal and explicit currency/rounding policy. Historical commission must not be recalculated from current settings.

## Refresh tokens and payments

Store only refresh-token hashes, rotate on use, detect reuse and revoke token family. Use Secure, HttpOnly, SameSite cookies and CSRF protection when cookie auth is introduced. Payment success requires verified provider webhook/reconciliation, never a browser redirect.
