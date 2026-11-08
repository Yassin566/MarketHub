# Transactional outbox delivery

The outbox is written in the same database transaction as the payment attempt.
Delivery uses short database claim/ack transactions and runs event handlers
outside database transactions.

## Worker behavior

The scheduled worker is disabled by default. Enable it with
`MARKETHUB_OUTBOX_WORKER_ENABLED=true` only after registering the handlers
required by the deployment. If no handlers are registered, the worker skips
polling and does not claim events.

Configuration (environment variables):

- `MARKETHUB_OUTBOX_WORKER_ENABLED`: defaults to `false`
- `MARKETHUB_OUTBOX_WORKER_POLL_INTERVAL`: defaults to `PT5S`
- `MARKETHUB_OUTBOX_WORKER_BATCH_SIZE`: defaults to `20`, range 1–100
- `MARKETHUB_OUTBOX_WORKER_LEASE_DURATION`: defaults to `PT2M`, maximum one hour
- `MARKETHUB_OUTBOX_WORKER_BASE_BACKOFF`: defaults to `PT10S`
- `MARKETHUB_OUTBOX_WORKER_MAX_BACKOFF`: defaults to `PT15M`, maximum one day

Handlers implement `OutboxEventHandler` and declare one event type. Duplicate
handler registrations fail application startup. A handler must be idempotent:
delivery is at-least-once, since a process can crash after performing its work
but before the database acknowledgement commits.

## Payment sandbox handler

The `PAYMENT_REQUESTED` event is handled by the sandbox payment adapter.
It uses the immutable payment UUID as provider idempotency key and stores a
deterministic sandbox reference. The adapter performs no network calls and
cannot charge or mark a payment successful. Provider processing is outside
database transactions; persistence uses a conditional update. The sandbox
provider mode is selected by `MARKETHUB_PAYMENT_PROVIDER_MODE=sandbox` and
is the default. The worker remains disabled by default.

## Delivery and retries

1. The worker claims a bounded batch using the lease-aware outbox service.
2. Each handler runs outside the claim transaction.
3. A successful handler is acknowledged only with its current lease token.
4. A failed event is rescheduled with capped exponential backoff. The outbox
   service moves events to `DEAD` after ten claims.
5. A stale worker cannot acknowledge or reschedule an event after its lease has
   been reclaimed.

The worker catches handler failures per event so one failing event does not
prevent the rest of the batch from being processed. Claim failures are logged
and the next scheduled poll retries.

## Safety boundary

No production payment gateway, provider credentials, webhook endpoint, or
payment/order success transition is introduced here. Sandbox dispatch only
moves a payment attempt from `REQUESTED` to `PROCESSING` and records a
non-financial provider reference. Orders remain `PENDING_PAYMENT`. A future
production integration must use provider-side idempotency and verified,
deduplicated provider callbacks; browser redirects must never mark an order paid.
