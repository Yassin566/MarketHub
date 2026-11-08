# Transactional outbox delivery primitives

The outbox is written in the same database transaction as the payment attempt.
This phase adds safe claiming and lease-fenced completion primitives; it does not
call a payment provider or claim that an event has been delivered.

## Worker contract

1. Call `claimBatch(batchSize, leaseDuration)` to atomically claim due events.
   PostgreSQL `FOR UPDATE SKIP LOCKED` allows concurrent workers to claim
   separate rows without holding locks while external work runs.
2. Process each returned event outside the database transaction.
3. Call `markDelivered(id, leaseToken)` only after the handler confirms success.
4. On a retryable failure, call `reschedule(id, leaseToken, error, backoff)`.
   The current lease token is required for either completion operation.

A claim increments the attempt count and issues a fresh lease token. Expired
processing leases can be reclaimed. Completion from an older worker is rejected
because its token no longer matches. Events are moved to `DEAD` after ten claims;
manual inspection/replay policy is intentionally a later operational feature.

## Safety boundary

No scheduler, provider adapter, provider credentials, outbound payment call,
webhook endpoint, or payment/order state transition is enabled by this phase.
The current `UNCONFIGURED` payment records remain pending. A future worker must
dispatch outside database transactions, use provider-side idempotency, and
verify signed provider callbacks before changing payment or order state.

The service is infrastructure only: callers must not mark an event delivered
until its handler has completed successfully.
