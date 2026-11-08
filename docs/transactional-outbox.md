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

## Delivery and retries

1. The worker claims a bounded batch using the lease-aware outbox service.
2. Each handler runs outside the claim transaction.
3. A successful handler is acknowledged only with its current lease token.
4. A failed or unhandled event is rescheduled with exponential backoff, capped
   by the configured maximum. The outbox service moves events to `DEAD` after
   ten claims.
5. A stale worker cannot acknowledge or reschedule an event after its lease has
   been reclaimed.

The worker catches handler failures per event so one failing event does not
prevent the rest of the batch from being processed. Claim failures are logged
and the next scheduled poll retries.

## Safety boundary

No payment handler, provider adapter, provider credentials, outbound payment
call, webhook endpoint, or payment/order state transition is introduced here.
No handler is registered by default and the worker is disabled by default.
Payment attempts therefore remain `REQUESTED` and orders remain
`PENDING_PAYMENT`. A future payment integration must use provider-side
idempotency and verified, deduplicated provider callbacks; browser redirects
must never mark an order paid.
