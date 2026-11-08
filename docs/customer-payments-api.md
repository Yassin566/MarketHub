# Customer payment requests

All customer endpoints require a customer access token. Customer ownership is derived from the authenticated principal.

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
- `MARKETHUB_PAYMENT_WEBHOOK_SECRET` is empty by default; set a strong secret to enable the sandbox webhook endpoint.

## Signed sandbox payment webhooks

POST /api/v1/webhooks/payments/sandbox

Headers:
- `X-MarketHub-Event-Id`: stable provider event identifier (max 120 characters)
- `X-MarketHub-Timestamp`: Unix timestamp in seconds
- `X-MarketHub-Signature`: `sha256=<hex HMAC-SHA256>`

The signature is HMAC-SHA256 over `<timestamp>.<exact raw request body>`. Timestamps outside a five-minute window are rejected. The JSON body contains `type` (`payment.succeeded` or `payment.failed`), `providerReference`, `amount`, and `currency`. Events are sandbox-only and must match the stored provider reference, amount, and currency.

Provider event IDs are unique per provider. Exact redeliveries return a duplicate result; reuse of an event ID with a different body is rejected. Successful payment events consume the still-active inventory reservations, decrement both on-hand and reserved stock, and mark the parent and vendor orders paid atomically. Failed events release active reservations and cancel a still-pending order. A late success after reservations expire is recorded as `REQUIRES_REVIEW`; it does not mark the order paid or consume inventory.

This endpoint is a sandbox integration/testing surface only. No production gateway, provider credentials, refund, or real financial transaction is implemented. Never mark an order paid from a browser redirect.

## Admin payment operations

Administrative users with the `ADMIN` or `SUPER_ADMIN` role can inspect payment attempts without changing payment state:

- `GET /api/v1/admin/payments?status=REQUIRES_REVIEW&limit=50&offset=0` lists payments, optionally filtered by a supported payment status.
- `GET /api/v1/admin/payments/{paymentId}/events` returns up to the latest 100 provider events for that payment, including event type, processing outcome, and timestamps.

The list defaults to 50 records and caps page size at 100. Supported status filters are `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED`, and `REQUIRES_REVIEW`. These endpoints are read-only; they do not replay callbacks, resolve reviews, or mark payments successful.

## Current scope boundary

Production use still requires a real provider adapter, provider-specific signature protocol and key rotation, secret storage, callback monitoring, reconciliation, refunds, and a late-payment compensation process. The sandbox HMAC protocol is not a substitute for a gateway's official webhook verification.
