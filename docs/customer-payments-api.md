# Customer payment requests

All endpoints require a customer access token. Customer ownership is derived from the authenticated principal.

## Request payment for an order

POST /api/v1/customer/orders/{orderId}/payments

Required header: `Idempotency-Key: <UUID>`

The endpoint creates one payment attempt and a `PAYMENT_REQUESTED` transactional-outbox event in the same PostgreSQL transaction. Repeating the same request with the same customer and idempotency key returns the original payment attempt. Reusing that key for a different order is rejected.

Payment requests are accepted only while the order is `PENDING_PAYMENT` and every inventory reservation remains active and unexpired. The payment amount and currency are copied from the order; the client cannot supply or override them.

## Current scope boundary

This phase persists the payment attempt and outbox event only. `providerCode` is `UNCONFIGURED`; no external provider is contacted, no payment is captured, and the order remains `PENDING_PAYMENT`. The outbox is durable and ready for a later dispatcher, but this phase does not yet claim delivery.

A later provider-specific phase must claim outbox rows safely, call the provider outside database transactions, verify signed webhooks, deduplicate provider event IDs, and reconcile late success after reservation expiry. A browser redirect must never mark an order paid.
