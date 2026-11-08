# Customer payment requests

All endpoints require a customer access token. Customer ownership is derived from the authenticated principal.

## Request payment for an order

POST /api/v1/customer/orders/{orderId}/payments

Required header: `Idempotency-Key: <UUID>`

The endpoint creates one payment attempt and a `PAYMENT_REQUESTED` transactional-outbox event in the same PostgreSQL transaction. Repeating the same request with the same customer and idempotency key returns the original payment attempt. Reusing that key for a different order is rejected.

Payment amount and currency are copied from the order; the client cannot supply or override them. Payment requests are accepted only while the order is `PENDING_PAYMENT` and every inventory reservation remains active and unexpired.

## Sandbox provider dispatch

The default provider mode is `sandbox`. The `PAYMENT_REQUESTED` handler uses the payment UUID as the provider idempotency key and stores the deterministic sandbox reference with payment status `PROCESSING`. Reprocessing the same outbox event returns the same sandbox reference. Provider work runs outside database transactions; the database update is conditional so a concurrent retry cannot overwrite a different payment state.

Configuration:

- `MARKETHUB_PAYMENT_PROVIDER_MODE=sandbox` selects the local deterministic adapter.
- `MARKETHUB_OUTBOX_WORKER_ENABLED=false` keeps dispatch disabled by default. Set it to `true` to run the scheduled worker in a local/test environment after confirming sandbox mode.

The sandbox adapter does not make network calls, create a real checkout session, charge money, or simulate a verified payment success. It does not mark orders paid or trigger fulfillment.

## Current scope boundary

There is no production payment gateway, provider credential handling, browser checkout redirect, webhook endpoint, refund, or payment/order success transition. The sandbox reference is an integration/testing primitive only. Production integrations must implement provider-side idempotency, signed callback verification, event deduplication, replay protection, and reconciliation. A browser redirect must never mark an order paid.
