# Architecture decisions

MarketHub is a modular monolith. Modules own their domain model and persistence; cross-module communication should use public application interfaces or domain events.

## Multi-tenancy
Vendor-owned aggregates carry vendor_id. Every vendor operation derives vendor identity from the authenticated principal and checks ownership in the application service.

## Checkout consistency
Order creation locks the customer's cart and product/inventory rows in stable product-ID order. It validates all lines and creates the parent order, per-vendor child orders, line-item snapshots, and expiring inventory reservations in one transaction. A failed line rolls back the entire checkout.

## Payment consistency
A customer payment request locks reservations and the parent order, verifies every reservation remains active, and writes the payment attempt and PAYMENT_REQUESTED outbox event atomically. The sandbox provider uses the payment UUID as its idempotency key and never calls a network. A signed sandbox webhook verifies HMAC over the timestamp and exact body, checks provider reference/amount/currency, and deduplicates event IDs. Successful callbacks atomically consume reserved inventory and mark payment, parent order, and vendor orders paid. Failed callbacks release active reservations and cancel a still-pending order. A success callback arriving after reservation expiry sets payment to REQUIRES_REVIEW and never marks the order paid. Browser redirects must never mark payment successful.

## Payment operations visibility
Administrative payment queries are read-only and restricted to ADMIN and SUPER_ADMIN. They expose bounded, paginated payment summaries, provider-event history, and stale-payment reconciliation attempts. These endpoints do not replay events or change payment state.

## Stale payment review
The optional scheduled worker selects old PROCESSING payments using bounded batches and FOR UPDATE SKIP LOCKED. In one transaction it records an immutable reconciliation attempt and changes only the payment status to REQUIRES_REVIEW. This is a timeout detector, not provider-status reconciliation: it never contacts a provider and never assumes a timeout means success or failure. It does not mutate orders or inventory. The worker is disabled by default; authoritative provider lookup and audited manual resolution are future work.

## Transactional outbox delivery
Outbox claims use FOR UPDATE SKIP LOCKED, bounded batches, attempt limits, and expiring lease tokens. The optional scheduled worker is disabled by default, invokes handlers outside claim transactions, and acknowledges/reschedules only with the current lease token. Delivery is at-least-once, so handlers must be idempotent.

## Orders and money
A parent order represents the customer's unified purchase; child vendor orders snapshot line items and currency. Current totals include product line items only. Taxes, shipping, discounts, commission, and payout snapshots remain future work. Use NUMERIC/BigDecimal and explicit currency/rounding policy.

## Refresh tokens
Store only refresh-token hashes, rotate on use, detect reuse and revoke token family. Use Secure, HttpOnly, SameSite cookies and CSRF protection when cookie auth is introduced.
