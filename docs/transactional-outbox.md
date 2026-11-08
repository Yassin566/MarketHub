# Transactional outbox delivery

The outbox is written in the same database transaction as the payment attempt. Delivery uses short database claim/ack transactions and runs event handlers outside database transactions.

## Worker behavior

The scheduled worker is disabled by default. Enable it with `MARKETHUB_OUTBOX_WORKER_ENABLED=true` only after registering the handlers required by the deployment. If no handlers are registered, the worker skips polling and does not claim events.

Configuration:
- `MARKETHUB_OUTBOX_WORKER_ENABLED`: defaults to `false`
- `MARKETHUB_OUTBOX_WORKER_POLL_INTERVAL`: defaults to `PT5S`
- `MARKETHUB_OUTBOX_WORKER_BATCH_SIZE`: defaults to `20`, range 1–100
- `MARKETHUB_OUTBOX_WORKER_LEASE_DURATION`: defaults to `PT2M`, maximum one hour
- `MARKETHUB_OUTBOX_WORKER_BASE_BACKOFF`: defaults to `PT10S`
- `MARKETHUB_OUTBOX_WORKER_MAX_BACKOFF`: defaults to `PT15M`, maximum one day

Handlers implement `OutboxEventHandler` and declare one event type. Duplicate handler registrations fail application startup. A handler must be idempotent: delivery is at-least-once.

## Payment dispatch and callbacks

The sandbox payment handler uses the immutable payment UUID as provider idempotency key and performs no network calls. It stores a deterministic reference and moves the payment attempt to `PROCESSING`. The signed sandbox webhook endpoint validates an HMAC over the timestamp and exact raw body, rejects stale timestamps, and deduplicates provider event IDs with a payload hash. Payment state, order state, and reservation/inventory changes commit atomically. Late success after reservation expiry is held in `REQUIRES_REVIEW` rather than marking the order paid.

## Delivery and retries

1. The worker claims a bounded batch using PostgreSQL `FOR UPDATE SKIP LOCKED`.
2. Handlers run outside the claim transaction.
3. A successful handler is acknowledged only with its current lease token.
4. Failures are rescheduled with capped exponential backoff; events become `DEAD` after ten claims.
5. A stale worker cannot acknowledge or reschedule an event after its lease has been reclaimed.

## Safety boundary

No production gateway, credentials, or real financial transaction is introduced. The webhook endpoint is a sandbox-only test surface. A production integration must use the provider's official signature-verification scheme, secret rotation, idempotency, reconciliation, and explicit compensation for late payments.
