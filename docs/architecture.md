# Architecture decisions

MarketHub is a modular monolith. Modules own their domain model and persistence; cross-module communication should use public application interfaces or domain events.

## Multi-tenancy
Vendor-owned aggregates carry vendor_id. Every vendor operation derives vendor identity from the authenticated principal and checks ownership in the application service. Never trust a vendor ID supplied by the browser as proof of ownership.

## Checkout consistency
Order creation locks the customer's cart and product/inventory rows in stable product-ID order. It validates all lines and creates the parent order, per-vendor child orders, line-item snapshots, and expiring inventory reservations in one database transaction. A failed line rolls back the entire checkout. Idempotency keys prevent duplicate orders on client retries. Do not hold database locks during remote payment calls. Persist pending checkout/order and an outbox event in one transaction; call payment provider outside it; finalize with signed, idempotent webhooks.

## Orders and money
A parent order represents the customer's unified purchase; child vendor orders snapshot line items and currency. This phase records product name, quantity, unit price, currency, line total, and order subtotal/total. Taxes, shipping, discounts, commission, and payout snapshots must be added before production payment/fulfillment. Use NUMERIC/BigDecimal and explicit currency/rounding policy. Historical commission must not be recalculated from current settings.

## Refresh tokens and payments
Store only refresh-token hashes, rotate on use, detect reuse and revoke token family. Use Secure, HttpOnly, SameSite cookies and CSRF protection when cookie auth is introduced. Payment success requires verified provider webhook/reconciliation, never a browser redirect.
